package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.pojos.TcpMonitor
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.event.EventSortField
import com.kuvaszuptime.kuvasz.models.dto.event.TcpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorsWithCategory
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageTcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.CategoryFilter
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.repositories.TcpUptimeEventRepository
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
class TcpMonitorActions(
    private val checkScheduler: TcpCheckScheduler,
    private val uptimeEventRepository: TcpUptimeEventRepository,
    monitorTypeSupport: TcpMonitorTypeSupport,
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
    MonitorActions<TcpMonitorRecord, TcpMonitorDetailsDto>(
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

    fun getMonitorDetails(monitorId: Long): TcpMonitorDetailsDto {
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
    ): List<TcpMonitorDetailsDto> =
        monitorRepository.getMonitorsWithDetails(
            enabled = enabled,
            uptimeStatus = uptimeStatus,
            sortedBy = sortedBy,
            categoryFilter = categoryFilter,
        ).withListDetails()

    fun getMonitorsPage(
        enabled: Boolean?,
        uptimeStatus: List<UptimeStatus>,
        categoryFilter: CategoryFilter?,
        name: String?,
        sorting: Sorting<TcpMonitorSortField>,
        pageRequest: PageRequest,
    ): PagedResult<TcpMonitorDetailsDto> {
        val page = monitorRepository.getMonitorsPage(
            enabled = enabled,
            uptimeStatus = uptimeStatus,
            categoryFilter = categoryFilter,
            name = name,
            sorting = sorting,
            pageRequest = pageRequest,
        )
        return page.withResult(page.result.withListDetails())
    }

    fun createMonitor(monitorCreateDto: TcpMonitorCreateDto): TcpMonitorRecord {
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

    fun updateMonitor(monitorId: Long, updates: ObjectNode): TcpMonitorRecord =
        updateMonitor(monitorId, updates, TcpMonitor::class.java, TcpMonitorUpdateDto::class.java) {
            TcpMonitorRecord(it)
        }

    override fun afterUpdate(existingMonitor: TcpMonitorRecord, updatedMonitor: TcpMonitorRecord, txCtx: DSLContext) {
        if (updatedMonitor.enabled) {
            checkScheduler.createChecksForMonitor(updatedMonitor)?.let { throw it }
        } else {
            checkScheduler.removeChecksOfMonitor(existingMonitor)
        }
        super.afterUpdate(existingMonitor, updatedMonitor, txCtx)
    }

    override fun afterDelete(deletedMonitor: TcpMonitorRecord) {
        // Remove any scheduled checks
        checkScheduler.removeChecksOfMonitor(deletedMonitor)
    }

    fun getUptimeEventsByMonitorId(monitorId: Long, limit: Int? = null): List<TcpUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor ->
                uptimeEventRepository.getEventsByMonitorId(monitor.id, limit)
            }

    fun getUptimeEventsPage(
        monitorId: Long,
        sorting: Sorting<EventSortField>,
        pageRequest: PageRequest,
    ): PagedResult<TcpUptimeEventDto> =
        monitorRepository.findById(monitorId, null)
            .orThrowNotFound(monitorId)
            .let { monitor -> uptimeEventRepository.getEventsPage(monitor.id, sorting, pageRequest) }

    fun getMonitorStats(monitorId: Long, period: Duration): TcpMonitorStatsDto =
        withUptimeHistory(monitorId, period) { monitor, uptimeHistory ->
            val statsDto = TcpMonitorStatsDto(
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

    fun getTcpMonitorsExport(): List<TcpMonitorRecord> = monitorRepository.fetchAll()

    override fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
        categories: List<String>?,
    ): List<StatusPageTcpMonitorDetailsDto> =
        buildStatusPageData(
            period = period,
            monitorIds = monitorIds,
            categories = categories,
            prefetch = { monitors ->
                metricsLogRepository.getAverageLatencies(
                    monitorIds = monitors.filter { it.metricsHistoryEnabled }.map { it.id },
                    period = period,
                )
            },
        ) { monitor, uptime, averageLatencies ->
            StatusPageTcpMonitorDetailsDto(
                name = monitor.name,
                lastCheck = monitor.lastUptimeCheck,
                averageLatencyInMs = averageLatencies[monitor.id],
                uptimeRatio = uptime.uptimeRatio,
                uptimeStatus = monitor.uptimeStatus,
                uptimeStatusHistory = uptime.uptimeStatusHistory,
                incidentCount = uptime.incidentCount,
                inMaintenance = uptime.inMaintenance,
                category = monitor.category,
            )
        }

    private fun List<TcpMonitorDetailsDto>.withListDetails(): List<TcpMonitorDetailsDto> {
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(monitorsWithCategory())
        return map { detailsDto ->
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
}
