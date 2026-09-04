package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.HttpMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.event.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.repositories.toStatsDto
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.services.monitor.MonitorActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageCacheInvalidator
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageMonitorDataProvider
import com.kuvaszuptime.kuvasz.validation.IntegrationIdValidator
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.jooq.SortField
import tools.jackson.databind.node.ObjectNode
import java.time.Duration

@Singleton
class HttpMonitorActions(
    monitorTypeSupport: HttpMonitorTypeSupport,
    private val checkScheduler: HttpCheckScheduler,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    dslContext: DSLContext,
    validator: Validator,
    integrationIdValidator: IntegrationIdValidator,
    private val integrationRepository: IntegrationRepository,
    eventDispatcher: EventDispatcher,
    statCalculator: StatCalculator,
    maintenanceWindowService: MaintenanceWindowService,
    statusPageRepository: StatusPageRepository,
    appConfig: AppConfig,
    statusPageCacheInvalidator: StatusPageCacheInvalidator,
) : StatusPageMonitorDataProvider,
    MonitorActions<HttpMonitorRecord, HttpMonitorDetailsDto>(
        dslContext,
        appConfig,
        statusPageRepository,
        eventDispatcher,
        statCalculator,
        maintenanceWindowService,
        monitorTypeSupport,
        validator,
        integrationIdValidator,
        statusPageCacheInvalidator,
    ) {

    private val monitorRepository = monitorTypeSupport.repository
    private val latencyLogRepository = monitorTypeSupport.metricsLogRepository

    fun getMonitorDetails(monitorId: Long): HttpMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        val windows = maintenanceWindowService.getWindowsForMonitor(monitorFromRepo.monitorId())

        return monitorFromRepo.copy(
            nextUptimeCheck = checkScheduler.getNextCheck(monitorId),
            nextSSLCheck = checkScheduler.getNextSSLCheck(monitorId),
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
    ): List<HttpMonitorDetailsDto> {
        val monitors =
            monitorRepository.getMonitorsWithDetails(enabled, uptimeStatus, sslStatus, sslCheckEnabled, sortedBy)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(monitors.map { it.monitorId() })

        return monitors.map { detailsDto ->
            val windows = windowsByMonitor[detailsDto.monitorId()].orEmpty()
            detailsDto.copy(
                nextUptimeCheck = checkScheduler.getNextCheck(detailsDto.id),
                nextSSLCheck = checkScheduler.getNextSSLCheck(detailsDto.id),
                effectiveIntegrations = integrationRepository.getEffectiveIntegrations(detailsDto.integrations)
                    .toSet(),
                maintenanceWindows = windows,
                inMaintenance = windows.any { it.active },
            )
        }
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
                announceCreation()
            }
    }

    override fun afterDelete(deletedMonitor: HttpMonitorRecord) {
        // Remove any scheduled checks
        checkScheduler.removeChecksOfMonitor(deletedMonitor)
    }

    // HTTP monitors expose their metrics history flag as `latencyHistoryEnabled`, which is part of the public API,
    // the YAML config and the backup format alike, while it is stored in the same column as for every other type
    override val patchAliases = mapOf(HttpMonitorUpdateDto::latencyHistoryEnabled.name to "metricsHistoryEnabled")

    fun updateMonitor(monitorId: Long, updates: ObjectNode): HttpMonitorRecord =
        updateMonitor(monitorId, updates, HttpMonitor::class.java, HttpMonitorUpdateDto::class.java) {
            HttpMonitorRecord(it)
        }

    override fun afterUpdate(existingMonitor: HttpMonitorRecord, updatedMonitor: HttpMonitorRecord, txCtx: DSLContext) {
        if (updatedMonitor.enabled) {
            checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
        } else {
            checkScheduler.removeChecksOfMonitor(existingMonitor)
        }
        super.afterUpdate(existingMonitor, updatedMonitor, txCtx)
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
        withUptimeHistory(monitorId, period) { monitor, uptimeHistory ->
            val statsDto = HttpMonitorStatsDto(
                id = monitor.id,
                uptimeHistory = uptimeHistory,
                latencyHistoryEnabled = monitor.metricsHistoryEnabled,
                latencyStats = null,
                latencyLogs = emptyList()
            )
            if (!monitor.metricsHistoryEnabled) {
                return@withUptimeHistory statsDto
            }

            statsDto.copy(
                latencyStats = latencyLogRepository.getLatencyMetrics(monitor.id, period)?.toStatsDto(),
                latencyLogs = latencyLogRepository.fetchLatestByMonitorId(monitor.id, period)
            )
        }

    fun getHttpMonitorsExport(): List<HttpMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageHttpMonitorDetailsDto> =
        buildStatusPageData(
            period = period,
            monitorIds = monitorIds,
        ) { monitor, uptime ->
            val latencyMetrics = if (monitor.latencyHistoryEnabled) {
                latencyLogRepository.getLatencyMetrics(monitor.id, period)
            } else {
                null
            }
            StatusPageHttpMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = latencyMetrics?.avg,
                uptimeRatio = uptime.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = uptime.uptimeStatusHistory,
                inMaintenance = uptime.inMaintenance,
            )
        }
}
