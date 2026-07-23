package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.DnsMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorNameException
import com.kuvaszuptime.kuvasz.models.dto.event.DnsUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.dns.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
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
class DnsMonitorActions(
    private val monitorRepository: DnsMonitorRepository,
    private val checkScheduler: DnsCheckScheduler,
    private val uptimeEventRepository: DnsUptimeEventRepository,
    private val metricsLogRepository: DnsMetricsLogRepository,
    private val dslContext: DSLContext,
    private val validator: Validator,
    private val integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
    private val eventDispatcher: EventDispatcher,
    private val statCalculator: StatCalculator,
    private val maintenanceWindowService: MaintenanceWindowService,
    statusPageRepository: StatusPageRepository,
    appConfig: AppConfig,
) : StatusPageMonitorDataProvider,
    MonitorActions<DnsMonitorRecord>(dslContext, appConfig, statusPageRepository, monitorRepository, eventDispatcher) {

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun getMonitorDetails(monitorId: Long): DnsMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        val windows = maintenanceWindowService.getWindowsForMonitor(monitorFromRepo.monitorId())

        return monitorFromRepo.copy(
            nextUptimeCheck = checkScheduler.getNextCheck(monitorId),
            effectiveIntegrations = integrationRepository
                .getEffectiveIntegrations(monitorFromRepo.integrations)
                .toSet(),
            maintenanceWindows = windows,
            inMaintenance = windows.any { it.active },
        )
    }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sortedBy: SortField<*>? = null,
    ): List<DnsMonitorDetailsDto> {
        val monitors = monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sortedBy)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(monitors.map { it.monitorId() })

        return monitors.map { detailsDto ->
            val windows = windowsByMonitor[detailsDto.monitorId()].orEmpty()
            detailsDto.copy(
                nextUptimeCheck = checkScheduler.getNextCheck(detailsDto.id),
                effectiveIntegrations = integrationRepository
                    .getEffectiveIntegrations(detailsDto.integrations)
                    .toSet(),
                maintenanceWindows = windows,
                inMaintenance = windows.any { it.active },
            )
        }
    }

    fun createMonitor(monitorCreateDto: DnsMonitorCreateDto): DnsMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations))
            .also { createdMonitor ->
                checkScheduler.createChecksForMonitor(createdMonitor)
            }
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): DnsMonitorRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingMonitor = monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId)
            val toUpdate = existingMonitor.into(DnsMonitor::class.java)
            val filteredUpdates = updates.propertyNames()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)
            // Check if name is present in a non-writable status page as reference
            if (updatedMonitor.name != existingMonitor.name && !isMonitorChangeable(existingMonitor)) {
                throw ReadOnlyMonitorNameException()
            }

            objectMapper.convertValue<DnsMonitorUpdateDto>(updatedMonitor).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Validate the raw integrations from the DTO
            updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

            DnsMonitorRecord(updatedMonitor).saveAndReschedule(existingMonitor, txCtx)
        }.also { updatedMonitorRecord ->
            eventDispatcher.dispatch(MonitorUpdateEvent(updatedMonitorRecord.numericMonitorId()))
        }

    private fun DnsMonitorRecord.saveAndReschedule(
        existingMonitor: DnsMonitorRecord,
        txCtx: DSLContext,
    ): DnsMonitorRecord =
        monitorRepository.returningUpdate(this, txCtx).also { updatedMonitor ->
            if (updatedMonitor.enabled) {
                checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
            } else {
                checkScheduler.removeChecksOfMonitor(existingMonitor)
            }
            // If the metrics history is disabled, we need to delete all the existing logs
            if (!updatedMonitor.metricsHistoryEnabled && existingMonitor.metricsHistoryEnabled) {
                metricsLogRepository.deleteAllByMonitorId(existingMonitor.id)
            }
        }

    fun deleteMonitorById(monitorId: Long) =
        super.deleteMonitorById(monitorId) { monitor ->
            checkScheduler.removeChecksOfMonitor(monitor)
        }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<DnsUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): DnsMonitorStatsDto =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                val uptimeHistory = statCalculator.calculateHistoricalDnsUptimeStats(period, monitorId)
                val statsDto = DnsMonitorStatsDto(
                    id = monitor.id,
                    metricsHistoryEnabled = monitor.metricsHistoryEnabled,
                    uptimeHistory = uptimeHistory,
                    latencyStats = null,
                    metricsLogs = emptyList(),
                )
                if (!monitor.metricsHistoryEnabled) {
                    return statsDto
                }
                val latencyMetrics = metricsLogRepository.getLatencyMetrics(monitor.id, period)
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
                    metricsLogs = metricsLogRepository.fetchLatestByMonitorId(monitor.id, period),
                )
            }

    fun getDnsMonitorsExport(): List<DnsMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageDnsMonitorDetailsDto> {
        val dnsMonitorNames = monitorIds?.filter { it.type == MonitorType.DNS }?.map { it.name }
        val enabledMonitors = monitorRepository.getMonitorsWithDetails(enabled = true, monitorNames = dnsMonitorNames)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(enabledMonitors.map { it.monitorId() })

        return enabledMonitors.map { monitor ->
            val uptimeHistory = statCalculator.calculateHistoricalDnsUptimeStats(period, monitor.id)
            val statusHistory = statCalculator.generateUptimeHistoryOverview(
                period = period,
                uptimeEvents = uptimeEventRepository.fetchAllInPeriod(
                    period = period,
                    monitorId = monitor.id,
                )
            )
            val latencyMetrics = monitor.metricsHistoryEnabled.takeIf { it }
                ?.let { metricsLogRepository.getLatencyMetrics(monitor.id, period) }

            StatusPageDnsMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = latencyMetrics?.avg,
                uptimeRatio = uptimeHistory.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = statusHistory,
                inMaintenance = windowsByMonitor[monitor.monitorId()].orEmpty().any { it.active },
            )
        }
    }
}
