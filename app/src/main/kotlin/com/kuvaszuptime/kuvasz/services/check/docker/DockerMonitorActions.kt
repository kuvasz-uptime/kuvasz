package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.DockerMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.event.DockerUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorsWithCategory
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.DockerUptimeEventRepository
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
class DockerMonitorActions(
    private val checkScheduler: DockerCheckScheduler,
    private val uptimeEventRepository: DockerUptimeEventRepository,
    monitorTypeSupport: DockerMonitorTypeSupport,
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
    MonitorActions<DockerMonitorRecord, DockerMonitorDetailsDto>(
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

    fun getMonitorDetails(monitorId: Long): DockerMonitorDetailsDto {
        val monitorFromRepo =
            monitorRepository.getMonitorWithDetails(monitorId) ?: throw MonitorNotFoundException(monitorId)
        val windows =
            maintenanceWindowService.getWindowsForMonitor(monitorFromRepo.monitorId(), monitorFromRepo.category)

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
        categoryFilter: CategoryFilter? = null,
    ): List<DockerMonitorDetailsDto> {
        val monitors = monitorRepository.getMonitorsWithDetails(
            enabled = enabled,
            uptimeStatus = uptimeStatus,
            sortedBy = sortedBy,
            categoryFilter = categoryFilter,
        )
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(monitors.monitorsWithCategory())

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

    fun createMonitor(monitorCreateDto: DockerMonitorCreateDto): DockerMonitorRecord {
        // Validate the raw integrations from the DTO
        val validatedIntegrations =
            integrationIdValidator.validateIntegrationIds(monitorCreateDto.integrations.orEmpty())

        val toCreate = monitorCreateDto.toMonitorRecord(validatedIntegrations).also { validateCreation(it) }

        return monitorRepository.returningInsert(toCreate)
            .also { createdMonitor ->
                checkScheduler.createChecksForMonitor(createdMonitor)
                announceCreation()
            }
    }

    fun updateMonitor(monitorId: Long, updates: ObjectNode): DockerMonitorRecord =
        updateMonitor(monitorId, updates, DockerMonitor::class.java, DockerMonitorUpdateDto::class.java) {
            DockerMonitorRecord(it)
        }

    override fun afterUpdate(
        existingMonitor: DockerMonitorRecord,
        updatedMonitor: DockerMonitorRecord,
        txCtx: DSLContext,
    ) {
        if (updatedMonitor.enabled) {
            checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
        } else {
            checkScheduler.removeChecksOfMonitor(existingMonitor)
        }
        super.afterUpdate(existingMonitor, updatedMonitor, txCtx)
    }

    override fun afterDelete(deletedMonitor: DockerMonitorRecord) {
        // Remove any scheduled checks
        checkScheduler.removeChecksOfMonitor(deletedMonitor)
    }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<DockerUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    /**
     * Unlike the other types this reports no latency: the only timing a Docker check produces is the round-trip to
     * the daemon, which is recorded for the metrics exporter but is not a measurement of the container.
     */
    fun getMonitorStats(monitorId: Long, period: Duration): DockerMonitorStatsDto =
        withUptimeHistory(monitorId, period) { monitor, uptimeHistory ->
            val statsDto = DockerMonitorStatsDto(
                id = monitor.id,
                metricsHistoryEnabled = monitor.metricsHistoryEnabled,
                uptimeHistory = uptimeHistory,
                cpuStats = null,
                memoryStats = null,
                metricsLogs = emptyList(),
            )
            if (!monitor.metricsHistoryEnabled) {
                return@withUptimeHistory statsDto
            }
            statsDto.copy(
                cpuStats = metricsLogRepository.getCpuUsageMetrics(monitor.id, period)?.toStatsDto(),
                memoryStats = metricsLogRepository.getMemoryUsageMetrics(monitor.id, period)?.toStatsDto(),
                metricsLogs = metricsLogRepository.fetchLatestByMonitorId(monitor.id, period),
            )
        }

    fun getDockerMonitorsExport(): List<DockerMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
        categories: List<String>?,
    ): List<StatusPageDockerMonitorDetailsDto> =
        buildStatusPageData(
            period = period,
            monitorIds = monitorIds,
            categories = categories,
        ) { monitor, uptime ->
            StatusPageDockerMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                uptimeRatio = uptime.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = uptime.uptimeStatusHistory,
                inMaintenance = uptime.inMaintenance,
                category = monitor.category,
            )
        }
}
