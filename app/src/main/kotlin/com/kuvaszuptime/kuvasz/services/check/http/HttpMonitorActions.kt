package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.HttpMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.CheckType
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorNameException
import com.kuvaszuptime.kuvasz.models.dto.event.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.LatencyStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.numericMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.http.toMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
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
class HttpMonitorActions(
    private val monitorRepository: HttpMonitorRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val checkScheduler: HttpCheckScheduler,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
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
    MonitorActions<HttpMonitorRecord>(dslContext, appConfig, statusPageRepository, monitorRepository, eventDispatcher) {

    private val objectMapper: ObjectMapper = jacksonMapperBuilder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    fun getMonitorDetails(monitorId: Long): HttpMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        val windows =
            maintenanceWindowService.getWindowsForMonitor(MonitorID(MonitorType.HTTP_SSL, monitorFromRepo.name))
        return monitorFromRepo.copy(
            nextUptimeCheck = checkScheduler.getNextCheck(CheckType.UPTIME, monitorId),
            nextSSLCheck = checkScheduler.getNextCheck(CheckType.SSL, monitorId),
            effectiveIntegrations = integrationRepository.getEffectiveIntegrations(monitorFromRepo.integrations)
                .toSet(),
            maintenanceWindows = windows,
            inMaintenance = windows.any { it.active },
        )
    }

    fun getMonitorsWithDetails(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus> = emptyList(),
        sslStatus: List<SslStatus> = emptyList(),
        sslCheckEnabled: Boolean? = null,
        sortedBy: SortField<*>? = null,
    ): List<HttpMonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sslStatus, sslCheckEnabled, sortedBy)
            .map { detailsDto ->
                val windows =
                    maintenanceWindowService.getWindowsForMonitor(MonitorID(MonitorType.HTTP_SSL, detailsDto.name))
                detailsDto.copy(
                    nextUptimeCheck = checkScheduler.getNextCheck(CheckType.UPTIME, detailsDto.id),
                    nextSSLCheck = checkScheduler.getNextCheck(CheckType.SSL, detailsDto.id),
                    effectiveIntegrations = integrationRepository.getEffectiveIntegrations(detailsDto.integrations)
                        .toSet(),
                    maintenanceWindows = windows,
                    inMaintenance = windows.any { it.active },
                )
            }

    fun createMonitor(monitorCreateDto: HttpMonitorCreateDto): HttpMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations))
            .also { insertedMonitor ->
                if (insertedMonitor.enabled) {
                    checkScheduler.createChecksForMonitor(insertedMonitor)?.let { schedulingError ->
                        monitorRepository.deleteById(insertedMonitor.id, null)
                        throw schedulingError
                    }
                }
            }
    }

    fun deleteMonitorById(monitorId: Long) =
        super.deleteMonitorById(monitorId) { deletedMonitor ->
            // Remove any scheduled checks
            checkScheduler.removeChecksOfMonitor(deletedMonitor)
        }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): HttpMonitorRecord =
        dslContext.transactionResultWithError { config ->
            val txCtx = config.dsl()
            val existingMonitor = monitorRepository.findById(monitorId, txCtx).orThrowNotFound(monitorId)
            val toUpdate = existingMonitor.into(HttpMonitor::class.java)
            val filteredUpdates = updates.propertyNames()
                .fold(objectMapper.createObjectNode()) { acc, fieldName ->
                    acc.set(fieldName, updates.get(fieldName))
                }
            val updatedMonitor = objectMapper.updateValue(toUpdate, filteredUpdates)
            // Check if name is present in a non-writable status page as reference
            if (updatedMonitor.name != existingMonitor.name && !isMonitorChangeable(existingMonitor)) {
                throw ReadOnlyMonitorNameException()
            }

            objectMapper.convertValue<HttpMonitorUpdateDto>(updatedMonitor).let { toValidate ->
                validator.validate(toValidate).throwIfNotEmpty()
            }
            // Validate the raw integrations from the DTO
            updatedMonitor.integrations?.let { integrationIdValidator.validateIntegrationIds(it) }

            HttpMonitorRecord(updatedMonitor).saveAndReschedule(existingMonitor, txCtx)
        }.also { updatedMonitorRecord ->
            eventDispatcher.dispatch(MonitorUpdateEvent(updatedMonitorRecord.numericMonitorId()))
        }

    private fun HttpMonitorRecord.saveAndReschedule(
        existingMonitor: HttpMonitorRecord,
        txCtx: DSLContext,
    ): HttpMonitorRecord =
        monitorRepository.returningUpdate(this, txCtx).also { updatedMonitor ->
            if (updatedMonitor.enabled) {
                checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
            } else {
                checkScheduler.removeChecksOfMonitor(existingMonitor)
            }
            // If the latency history is disabled, we need to delete all the existing logs
            if (!updatedMonitor.latencyHistoryEnabled && existingMonitor.latencyHistoryEnabled) {
                latencyLogRepository.deleteAllByMonitorId(existingMonitor.id)
            }
        }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<HttpUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getSSLEventsByMonitorId(monitorId: Long, limit: Int? = null): List<SSLEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                sslEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): HttpMonitorStatsDto =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                val uptimeHistory = statCalculator.calculateHistoricalHttpUptimeStats(period, monitorId)
                val statsDto = HttpMonitorStatsDto(
                    id = monitor.id,
                    uptimeHistory = uptimeHistory,
                    latencyHistoryEnabled = monitor.latencyHistoryEnabled,
                    latencyStats = null,
                    latencyLogs = emptyList()
                )
                if (!monitor.latencyHistoryEnabled) {
                    return statsDto
                }

                val metrics = latencyLogRepository.getLatencyMetrics(monitor.id, period)
                statsDto.copy(
                    latencyStats = metrics?.let {
                        LatencyStatsDto(
                            averageLatencyInMs = metrics.avg,
                            minLatencyInMs = metrics.min,
                            maxLatencyInMs = metrics.max,
                            p90LatencyInMs = metrics.p90,
                            p95LatencyInMs = metrics.p95,
                            p99LatencyInMs = metrics.p99,
                        )
                    },
                    latencyLogs = latencyLogRepository.fetchLatestByMonitorId(monitor.id, period)
                )
            }

    fun getHttpMonitorsExport(): List<HttpMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageHttpMonitorDetailsDto> {
        val httpMonitorNames = monitorIds?.filter { it.type == MonitorType.HTTP_SSL }?.map { it.name }
        val enabledMonitors = monitorRepository.getMonitorsWithDetails(enabled = true, monitorNames = httpMonitorNames)

        return enabledMonitors.map { monitor ->
            val uptimeHistory = statCalculator.calculateHistoricalHttpUptimeStats(period, monitor.id)
            val latencyMetrics = if (monitor.latencyHistoryEnabled) {
                latencyLogRepository.getLatencyMetrics(monitor.id, period)
            } else {
                null
            }
            val statusHistory = statCalculator.generateUptimeHistoryOverview(
                period = period,
                uptimeEvents = uptimeEventRepository.fetchAllInPeriod(
                    period = period,
                    monitorId = monitor.id,
                )
            )
            StatusPageHttpMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = latencyMetrics?.avg,
                uptimeRatio = uptimeHistory.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = statusHistory,
            )
        }
    }
}
