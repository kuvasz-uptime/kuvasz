package com.kuvaszuptime.kuvasz.services.check.dns

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.DnsMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.event.DnsUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.dns.deduplicated
import com.kuvaszuptime.kuvasz.repositories.DnsUptimeEventRepository
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
class DnsMonitorActions(
    private val checkScheduler: DnsCheckScheduler,
    private val uptimeEventRepository: DnsUptimeEventRepository,
    monitorTypeSupport: DnsMonitorTypeSupport,
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
    MonitorActions<DnsMonitorRecord, DnsMonitorDetailsDto>(
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
    private val metricsLogRepository = monitorTypeSupport.metricsLogRepository

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
                announceCreation()
            }
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): DnsMonitorRecord =
        updateMonitor(monitorId, updates, DnsMonitor::class.java, DnsMonitorUpdateDto::class.java) {
            DnsMonitorRecord(it).deduplicated()
        }

    override fun afterUpdate(existingMonitor: DnsMonitorRecord, updatedMonitor: DnsMonitorRecord, txCtx: DSLContext) {
        if (updatedMonitor.enabled) {
            checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
        } else {
            checkScheduler.removeChecksOfMonitor(existingMonitor)
        }
        super.afterUpdate(existingMonitor, updatedMonitor, txCtx)
    }

    override fun afterDelete(deletedMonitor: DnsMonitorRecord) {
        // Remove any scheduled checks
        checkScheduler.removeChecksOfMonitor(deletedMonitor)
    }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<DnsUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): DnsMonitorStatsDto =
        withUptimeHistory(monitorId, period) { monitor, uptimeHistory ->
            val statsDto = DnsMonitorStatsDto(
                id = monitor.id,
                metricsHistoryEnabled = monitor.metricsHistoryEnabled,
                uptimeHistory = uptimeHistory,
                latencyStats = null,
                metricsLogs = emptyList(),
            )
            if (!monitor.metricsHistoryEnabled) {
                return@withUptimeHistory statsDto
            }
            statsDto.copy(
                latencyStats = metricsLogRepository.getLatencyMetrics(monitor.id, period)?.toStatsDto(),
                metricsLogs = metricsLogRepository.fetchLatestByMonitorId(monitor.id, period),
            )
        }

    fun getDnsMonitorsExport(): List<DnsMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageDnsMonitorDetailsDto> =
        buildStatusPageData(
            period = period,
            monitorIds = monitorIds,
        ) { monitor, uptime ->
            val latencyMetrics = monitor.metricsHistoryEnabled.takeIf { it }
                ?.let { metricsLogRepository.getLatencyMetrics(monitor.id, period) }

            StatusPageDnsMonitorDetailsDto(
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
