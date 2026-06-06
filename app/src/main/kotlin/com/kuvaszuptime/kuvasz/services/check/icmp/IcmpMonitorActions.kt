package com.kuvaszuptime.kuvasz.services.check.icmp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.IcmpMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorNameException
import com.kuvaszuptime.kuvasz.models.dto.event.IcmpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.PacketLossStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.icmp.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.icmp.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.monitor.MonitorActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageMonitorDataProvider
import com.kuvaszuptime.kuvasz.util.transactionResultWithError
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.time.Duration

@Singleton
class IcmpMonitorActions(
    private val monitorRepository: IcmpMonitorRepository,
    private val checkScheduler: IcmpCheckScheduler,
    private val uptimeEventRepository: IcmpUptimeEventRepository,
    private val metricsLogRepository: IcmpMetricsLogRepository,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
    private val eventDispatcher: EventDispatcher,
    private val statCalculator: StatCalculator,
    statusPageRepository: StatusPageRepository,
    appConfig: AppConfig,
) : StatusPageMonitorDataProvider,
    MonitorActions<IcmpMonitorRecord>(dslContext, appConfig, statusPageRepository, monitorRepository, eventDispatcher) {

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun getMonitorDetails(monitorId: Long): IcmpMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        return monitorFromRepo.copy(
            nextUptimeCheck = checkScheduler.getNextCheck(monitorId),
            effectiveIntegrations = integrationRepository
                .getEffectiveIntegrations(monitorFromRepo.integrations)
                .toSet()
        )
    }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
    ): List<IcmpMonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sortedBy)
            .map { detailsDto ->
                detailsDto.copy(
                    nextUptimeCheck = checkScheduler.getNextCheck(detailsDto.id),
                    effectiveIntegrations = integrationRepository
                        .getEffectiveIntegrations(detailsDto.integrations)
                        .toSet()
                )
            }

    fun createMonitor(monitorCreateDto: IcmpMonitorCreateDto): IcmpMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations))
            .also { createdMonitor ->
                checkScheduler.createChecksForMonitor(createdMonitor)
            }
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): IcmpMonitorRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingMonitor = monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId)
            val toUpdate = existingMonitor.into(IcmpMonitor::class.java)
            val filteredUpdates = updates.propertyNames()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)
            // Check if name is present in a non-writable status page as reference
            if (updatedMonitor.name != existingMonitor.name && !isMonitorChangeable(existingMonitor)) {
                throw ReadOnlyMonitorNameException()
            }

            objectMapper.convertValue<IcmpMonitorUpdateDto>(updatedMonitor).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Validate the raw integrations from the DTO
            updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

            IcmpMonitorRecord(updatedMonitor).saveAndReschedule(existingMonitor, txCtx)
        }.also { updatedMonitorRecord ->
            eventDispatcher.dispatch(MonitorUpdateEvent(updatedMonitorRecord.numericMonitorId()))
        }

    private fun IcmpMonitorRecord.saveAndReschedule(
        existingMonitor: IcmpMonitorRecord,
        txCtx: DSLContext,
    ): IcmpMonitorRecord =
        monitorRepository.returningUpdate(this, txCtx).fold(
            { persistenceError -> throw persistenceError },
            { updatedMonitor ->
                if (updatedMonitor.enabled) {
                    checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
                } else {
                    checkScheduler.removeChecksOfMonitor(existingMonitor)
                }
                // If the metrics history is disabled, we need to delete all the existing logs
                if (!updatedMonitor.metricsHistoryEnabled && existingMonitor.metricsHistoryEnabled) {
                    metricsLogRepository.deleteAllByMonitorId(existingMonitor.id)
                }
                updatedMonitor
            }
        )

    fun deleteMonitorById(monitorId: Long) =
        super.deleteMonitorById(monitorId) { monitor ->
            checkScheduler.removeChecksOfMonitor(monitor)
        }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<IcmpUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): IcmpMonitorStatsDto =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                val uptimeHistory = statCalculator.calculateHistoricalIcmpUptimeStats(period, monitorId)
                val statsDto = IcmpMonitorStatsDto(
                    id = monitor.id,
                    metricsHistoryEnabled = monitor.metricsHistoryEnabled,
                    uptimeHistory = uptimeHistory,
                    latencyStats = null,
                    packetLossStats = null,
                    metricsLogs = emptyList(),
                )
                if (!monitor.metricsHistoryEnabled) {
                    return statsDto
                }
                val latencyMetrics = metricsLogRepository.getLatencyMetrics(monitor.id, period)
                val packetLossMetrics = metricsLogRepository.getPacketLossMetrics(monitor.id, period)
                statsDto.copy(
                    latencyStats = latencyMetrics?.let {
                        LatencyStatsDto(
                            averageLatencyInMs = latencyMetrics.avg,
                            minLatencyInMs = latencyMetrics.min,
                            maxLatencyInMs = latencyMetrics.max,
                            p90LatencyInMs = latencyMetrics.p90,
                            p95LatencyInMs = latencyMetrics.p95,
                            p99LatencyInMs = latencyMetrics.p99,
                        )
                    },
                    packetLossStats = packetLossMetrics?.let {
                        PacketLossStatsDto(
                            averagePacketLossPercentage = packetLossMetrics.avg,
                            minPacketLossPercentage = packetLossMetrics.min,
                            maxPacketLossPercentage = packetLossMetrics.max,
                            p90PacketLossPercentage = packetLossMetrics.p90,
                            p95PacketLossPercentage = packetLossMetrics.p95,
                            p99PacketLossPercentage = packetLossMetrics.p99,
                        )
                    },
                    metricsLogs = metricsLogRepository.fetchLatestByMonitorId(monitor.id, period),
                )
            }

    fun getIcmpMonitorsExport(): List<IcmpMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageIcmpMonitorDetailsDto> {
        val icmpMonitorNames = monitorIds?.filter { it.type == MonitorType.ICMP }?.map { it.name }
        val enabledMonitors = monitorRepository.getMonitorsWithDetails(enabled = true, monitorNames = icmpMonitorNames)

        return enabledMonitors.map { monitor ->
            val uptimeHistory = statCalculator.calculateHistoricalIcmpUptimeStats(period, monitor.id)
            val statusHistory = statCalculator.generateUptimeHistoryOverview(
                period = period,
                uptimeEvents = uptimeEventRepository.fetchAllInPeriod(
                    period = period,
                    monitorId = monitor.id,
                )
            )
            val latencyMetrics = monitor.metricsHistoryEnabled.takeIf { it }
                ?.let { metricsLogRepository.getLatencyMetrics(monitor.id, period) }
            val packetLossMetrics = monitor.metricsHistoryEnabled.takeIf { it }
                ?.let { metricsLogRepository.getPacketLossMetrics(monitor.id, period) }

            StatusPageIcmpMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = latencyMetrics?.avg,
                lastPacketLossPercentage = packetLossMetrics?.avg,
                uptimeRatio = uptimeHistory.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = statusHistory,
            )
        }
    }
}
