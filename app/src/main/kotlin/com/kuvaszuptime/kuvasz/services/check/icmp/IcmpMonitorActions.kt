package com.kuvaszuptime.kuvasz.services.check.icmp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.IcmpMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.event.IcmpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
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
class IcmpMonitorActions(
    private val checkScheduler: IcmpCheckScheduler,
    private val uptimeEventRepository: IcmpUptimeEventRepository,
    monitorTypeSupport: IcmpMonitorTypeSupport,
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
    MonitorActions<IcmpMonitorRecord, IcmpMonitorDetailsDto>(
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

    fun getMonitorDetails(monitorId: Long): IcmpMonitorDetailsDto {
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
    ): List<IcmpMonitorDetailsDto> {
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

    fun createMonitor(monitorCreateDto: IcmpMonitorCreateDto): IcmpMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        return monitorRepository.returningInsert(monitorCreateDto.toMonitorRecord(validatedIntegrations))
            .also { createdMonitor ->
                checkScheduler.createChecksForMonitor(createdMonitor)
                announceCreation()
            }
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): IcmpMonitorRecord =
        updateMonitor(monitorId, updates, IcmpMonitor::class.java, IcmpMonitorUpdateDto::class.java) {
            IcmpMonitorRecord(it)
        }

    override fun afterUpdate(existingMonitor: IcmpMonitorRecord, updatedMonitor: IcmpMonitorRecord, txCtx: DSLContext) {
        if (updatedMonitor.enabled) {
            checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
        } else {
            checkScheduler.removeChecksOfMonitor(existingMonitor)
        }
        super.afterUpdate(existingMonitor, updatedMonitor, txCtx)
    }

    override fun afterDelete(deletedMonitor: IcmpMonitorRecord) {
        // Remove any scheduled checks
        checkScheduler.removeChecksOfMonitor(deletedMonitor)
    }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<IcmpUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getMonitorStats(monitorId: Long, period: Duration): IcmpMonitorStatsDto =
        withUptimeHistory(monitorId, period) { monitor, uptimeHistory ->
            val statsDto = IcmpMonitorStatsDto(
                id = monitor.id,
                metricsHistoryEnabled = monitor.metricsHistoryEnabled,
                uptimeHistory = uptimeHistory,
                latencyStats = null,
                packetLossStats = null,
                metricsLogs = emptyList(),
            )
            if (!monitor.metricsHistoryEnabled) {
                return@withUptimeHistory statsDto
            }
            statsDto.copy(
                latencyStats = metricsLogRepository.getLatencyMetrics(monitor.id, period)?.toStatsDto(),
                packetLossStats = metricsLogRepository.getPacketLossMetrics(monitor.id, period)?.toStatsDto(),
                metricsLogs = metricsLogRepository.fetchLatestByMonitorId(monitor.id, period),
            )
        }

    fun getIcmpMonitorsExport(): List<IcmpMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageIcmpMonitorDetailsDto> =
        buildStatusPageData(
            period = period,
            monitorIds = monitorIds,
        ) { monitor, uptime ->
            val latencyMetrics = monitor.metricsHistoryEnabled.takeIf { it }
                ?.let { metricsLogRepository.getLatencyMetrics(monitor.id, period) }
            val packetLossMetrics = monitor.metricsHistoryEnabled.takeIf { it }
                ?.let { metricsLogRepository.getPacketLossMetrics(monitor.id, period) }

            StatusPageIcmpMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = latencyMetrics?.avg,
                lastPacketLossPercentage = packetLossMetrics?.avg,
                uptimeRatio = uptime.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = uptime.uptimeStatusHistory,
                inMaintenance = uptime.inMaintenance,
            )
        }
}
