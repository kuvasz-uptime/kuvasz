package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMaintenanceWindowDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceInterval
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowCalculator
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.cache.annotation.Cacheable
import jakarta.inject.Singleton
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class StatusPageDataActions(
    private val statusPageRepository: StatusPageRepository,
    private val monitorDataProviders: List<StatusPageMonitorDataProvider>,
    private val defaultStatusPageConfig: DefaultStatusPageConfig,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val maintenanceWindowCalculator: MaintenanceWindowCalculator,
) {
    companion object {
        const val STATUS_PAGES_CACHE_NAME = "status-pages"
        const val DEFAULT_PAGE_CACHE_NAME = "default-status-page"
        private const val DEFAULT_METRICS_PERIOD = "P30D"
        private val UPCOMING_LOOKAHEAD: Duration = Duration.ofHours(24)
    }

    @Cacheable(DEFAULT_PAGE_CACHE_NAME)
    fun getCachedDefaultStatusPageData() = getDefaultStatusPageData()

    fun getDefaultStatusPageData(): StatusPageDataDto {
        val monitors = monitorDataProviders.flatMap { provider ->
            provider.getStatusPageDataOfEnabledMonitors(
                period = Duration.parse(DEFAULT_METRICS_PERIOD),
                monitorIds = null,
            )
        }

        val activeAndUpcomingWindows = resolveMaintenanceWindows(monitors)

        return StatusPageDataDto(
            title = defaultStatusPageConfig.title,
            customLogoUrl = defaultStatusPageConfig.customLogoUrl,
            customFaviconUrl = defaultStatusPageConfig.customFaviconUrl,
            systemStatus = calculateSystemStatus(monitors),
            generatedAt = getCurrentTimestamp(),
            monitors = monitors,
            activeMaintenanceWindows = activeAndUpcomingWindows.active,
            upcomingMaintenanceWindows = activeAndUpcomingWindows.upcoming,
        )
    }

    private fun calculateSystemStatus(monitors: List<StatusPageMonitorDetailsDto>) =
        if (monitors.isEmpty()) {
            SystemStatus.PENDING
        } else {
            val monitorStatusMap = monitors.groupBy { it.uptimeStatus }
            val monitorCnt = monitors.size
            val upCnt = monitorStatusMap[UptimeStatus.UP]?.size ?: 0
            val downCnt = monitorStatusMap[UptimeStatus.DOWN]?.size ?: 0
            val maintenanceCnt = monitors.count { it.inMaintenance }
            when {
                // Outages always take precedence over maintenance
                downCnt == monitorCnt -> SystemStatus.MAJOR_OUTAGE
                upCnt > 0 && downCnt > 0 -> SystemStatus.PARTIAL_OUTAGE
                // Maintenance takes precedence over the operational/pending states
                downCnt == 0 && maintenanceCnt == monitorCnt -> SystemStatus.MAINTENANCE
                downCnt == 0 && maintenanceCnt > 0 -> SystemStatus.PARTIAL_MAINTENANCE
                upCnt == monitorCnt -> SystemStatus.OPERATIONAL
                else -> SystemStatus.PENDING
            }
        }

    @Cacheable(STATUS_PAGES_CACHE_NAME)
    fun getCachedStatusPageData(statusPageId: Long) = getStatusPageData(statusPageId)

    fun getStatusPageData(statusPageId: Long): StatusPageDataDto {
        val statusPage = statusPageRepository.findById(statusPageId).orThrowNotFound(statusPageId.toString())
        val monitors = monitorDataProviders.flatMap { provider ->
            provider.getStatusPageDataOfEnabledMonitors(
                period = Duration.parse(DEFAULT_METRICS_PERIOD),
                monitorIds = statusPage.monitors?.toList(),
            )
        }

        val activeAndUpcomingWindows = resolveMaintenanceWindows(monitors)

        return StatusPageDataDto(
            title = statusPage.title,
            customLogoUrl = statusPage.customLogoUrl,
            customFaviconUrl = statusPage.customFaviconUrl,
            generatedAt = getCurrentTimestamp(),
            systemStatus = calculateSystemStatus(monitors),
            monitors = monitors,
            activeMaintenanceWindows = activeAndUpcomingWindows.active,
            upcomingMaintenanceWindows = activeAndUpcomingWindows.upcoming,
        )
    }

    private fun resolveMaintenanceWindows(
        monitors: List<StatusPageMonitorDetailsDto>,
    ): ActiveAndUpcomingWindows {
        val pageMonitorIds = monitors.mapNotNullTo(mutableSetOf()) { monitor ->
            MonitorType.fromIdentifier(monitor.type)?.let { type -> MonitorID(type, monitor.name) }
        }
        val now = getCurrentTimestamp()
        val upcomingUntil = now.plus(UPCOMING_LOOKAHEAD)

        val relevantWindows = maintenanceWindowRepository.fetchEnabledOnStatusPages()
            .filter { window -> window.affectsAnyOf(pageMonitorIds) }

        val active = relevantWindows
            .filter { maintenanceWindowCalculator.isActive(it, now) }
            .map { window ->
                StatusPageMaintenanceWindowDto.fromWindowAndInterval(
                    window,
                    maintenanceWindowCalculator.currentInterval(window, now),
                )
            }
            .sortedBy { it.start ?: OffsetDateTime.MIN }

        val upcoming = relevantWindows
            .filterNot { maintenanceWindowCalculator.isActive(it, now) }
            .mapNotNull { window ->
                maintenanceWindowCalculator.nextInterval(window, now)
                    ?.takeIf { !it.start.isAfter(upcomingUntil) }
                    ?.let { interval -> StatusPageMaintenanceWindowDto.fromWindowAndInterval(window, interval) }
            }
            .sortedBy { it.start ?: OffsetDateTime.MIN }

        return ActiveAndUpcomingWindows(active, upcoming)
    }

    private fun StatusPageMaintenanceWindowDto.Companion.fromWindowAndInterval(
        window: MaintenanceWindowRecord,
        interval: MaintenanceInterval?,
    ) = StatusPageMaintenanceWindowDto(
        name = window.name,
        description = window.description,
        start = interval?.start,
        end = interval?.end,
    )

    private fun MaintenanceWindowRecord.affectsAnyOf(monitorIds: Set<MonitorID>): Boolean =
        global || monitors.any { it in monitorIds }

    private data class ActiveAndUpcomingWindows(
        val active: List<StatusPageMaintenanceWindowDto>,
        val upcoming: List<StatusPageMaintenanceWindowDto>,
    )
}
