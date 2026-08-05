package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.monitorType
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

@Singleton
class StatCalculator(
    monitorRepositories: List<MonitorRepository<*, *>>,
    uptimeEventRepositories: List<UptimeEventRepository>,
    private val maintenanceWindowService: MaintenanceWindowService,
) {
    private val monitorReposByType = monitorRepositories.associateBy { it.monitorType }
    private val uptimeEventReposByType = uptimeEventRepositories.associateBy { it.monitorType }

    fun calculateOverallHttpStats(period: Duration): HttpMonitoringStatsDto {
        val overallStats = calculateOverallStats(MonitorType.HTTP_SSL, period)

        return HttpMonitoringStatsDto(
            actual = HttpMonitoringStatsDto.ActualMonitoringStats(
                uptimeStats = overallStats.uptimeStats,
                sslStats = calculateSslStats(overallStats.monitors),
            ),
            history = HttpMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = overallStats.historicalUptimeStats
            )
        )
    }

    fun calculateOverallPushStats(period: Duration): PushMonitoringStatsDto {
        val overallStats = calculateOverallStats(MonitorType.PUSH, period)

        return PushMonitoringStatsDto(
            actual = PushMonitoringStatsDto.ActualMonitoringStats(uptimeStats = overallStats.uptimeStats),
            history = PushMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = overallStats.historicalUptimeStats
            )
        )
    }

    fun calculateOverallIcmpStats(period: Duration): IcmpMonitoringStatsDto {
        val overallStats = calculateOverallStats(MonitorType.ICMP, period)

        return IcmpMonitoringStatsDto(
            actual = IcmpMonitoringStatsDto.ActualMonitoringStats(uptimeStats = overallStats.uptimeStats),
            history = IcmpMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = overallStats.historicalUptimeStats
            )
        )
    }

    fun calculateOverallTcpStats(period: Duration): TcpMonitoringStatsDto {
        val overallStats = calculateOverallStats(MonitorType.TCP, period)

        return TcpMonitoringStatsDto(
            actual = TcpMonitoringStatsDto.ActualMonitoringStats(uptimeStats = overallStats.uptimeStats),
            history = TcpMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = overallStats.historicalUptimeStats
            )
        )
    }

    fun calculateOverallDnsStats(period: Duration): DnsMonitoringStatsDto {
        val overallStats = calculateOverallStats(MonitorType.DNS, period)

        return DnsMonitoringStatsDto(
            actual = DnsMonitoringStatsDto.ActualMonitoringStats(uptimeStats = overallStats.uptimeStats),
            history = DnsMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = overallStats.historicalUptimeStats
            )
        )
    }

    /**
     * Calculates the overall - both actual and historical - uptime statistics of every monitor of the given type.
     */
    private fun calculateOverallStats(monitorType: MonitorType, period: Duration): OverallStats {
        val monitors = monitorReposByType.getValue(monitorType).fetchAllWithDetails()
        val uptimeEventRepository = uptimeEventReposByType.getValue(monitorType)
        val uptimeEvents = uptimeEventRepository.fetchAllInPeriod(period)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(
            monitorIds = monitors.filter { it.enabled }.map { it.monitorId() }
        )
        var downMonitors = 0
        var upMonitors = 0
        var pausedMonitors = 0
        var uptimeInProgressMonitors = 0
        var inMaintenanceMonitors = 0

        monitors.forEach { monitor ->
            if (!monitor.enabled) {
                pausedMonitors++
                return@forEach
            }
            when (monitor.uptimeStatus) {
                UptimeStatus.DOWN -> downMonitors++
                UptimeStatus.UP -> upMonitors++
                null -> uptimeInProgressMonitors++
            }
            if (windowsByMonitor[monitor.monitorId()]?.any { it.active } == true) {
                inMaintenanceMonitors++
            }
        }

        return OverallStats(
            monitors = monitors,
            uptimeStats = ActualUptimeStats(
                total = monitors.size,
                down = downMonitors,
                up = upMonitors,
                paused = pausedMonitors,
                inProgress = uptimeInProgressMonitors,
                inMaintenance = inMaintenanceMonitors,
                lastIncident = uptimeEventRepository.fetchLatestIncidentTimestamp(),
            ),
            historicalUptimeStats = calculateHistoricalUptimeStats(period, uptimeEvents),
        )
    }

    /**
     * Calculates the SSL statistics of the HTTP monitors that have their SSL checks enabled.
     */
    private fun calculateSslStats(
        monitors: List<MonitorDetailsDto>,
    ): HttpMonitoringStatsDto.ActualMonitoringStats.SslStats {
        var validMonitors = 0
        var invalidMonitors = 0
        var willExpireMonitors = 0
        var inProgressMonitors = 0

        monitors
            .filterIsInstance<HttpMonitorDetailsDto>()
            .filter { it.enabled && it.sslCheckEnabled }
            .forEach { monitor ->
                when (monitor.sslStatus) {
                    SslStatus.VALID -> validMonitors++
                    SslStatus.INVALID -> invalidMonitors++
                    SslStatus.WILL_EXPIRE -> willExpireMonitors++
                    null -> inProgressMonitors++
                }
            }

        return HttpMonitoringStatsDto.ActualMonitoringStats.SslStats(
            invalid = invalidMonitors,
            valid = validMonitors,
            willExpire = willExpireMonitors,
            inProgress = inProgressMonitors,
        )
    }

    /**
     * Calculates historical uptime statistics for a specific monitor over a given period.
     */
    fun calculateHistoricalUptimeStats(
        monitorType: MonitorType,
        period: Duration,
        monitorId: Long,
    ): HistoricalUptimeStatsDto =
        calculateHistoricalUptimeStats(period, fetchUptimeEventsInPeriod(monitorType, period, listOf(monitorId)))

    /**
     * Calculates historical uptime statistics based on a list of uptime events and a period's start time.
     *
     * @param now The instant both ends of the period are anchored to. Otherwise the ongoing events would be measured
     * against a slightly later "now" than the one the period start was derived from, inflating their durations.
     */
    private fun calculateHistoricalUptimeStats(
        period: Duration,
        uptimeEvents: List<UptimeEventCalculationContext>,
        now: OffsetDateTime = getCurrentTimestamp(),
    ): HistoricalUptimeStatsDto {
        val periodStart = now.minus(period)
        val monitorsWithIncidents: MutableSet<Long> = mutableSetOf()
        var historicalIncidentCnt = 0
        var historicalUptimeSeconds = 0L
        var historicalDowntimeSeconds = 0L

        uptimeEvents.forEach { uptimeEvent ->
            if (!uptimeEvent.isMonitorEnabled && uptimeEvent.updatedAt.isBefore(periodStart)) {
                // If the monitor was disabled and the last update was before the period then we skip this event
                return@forEach
            }
            val duration = getDurationOfEvent(
                isMonitorEnabled = uptimeEvent.isMonitorEnabled,
                startedAt = uptimeEvent.effectiveStartDate(limitDate = periodStart),
                endedAt = uptimeEvent.endedAt,
                updatedAt = uptimeEvent.updatedAt,
                now = now,
            )

            if (uptimeEvent.status == UptimeStatus.DOWN) {
                monitorsWithIncidents.add(uptimeEvent.monitorId)
                historicalIncidentCnt++
                historicalDowntimeSeconds += duration
            } else if (uptimeEvent.status == UptimeStatus.UP) {
                historicalUptimeSeconds += duration
            }
        }
        val totalMeasuredSeconds = historicalUptimeSeconds + historicalDowntimeSeconds

        return HistoricalUptimeStatsDto(
            period = period.toString(),
            incidents = historicalIncidentCnt,
            affectedMonitors = monitorsWithIncidents.size,
            uptimeRatio = if (totalMeasuredSeconds > 0) {
                historicalUptimeSeconds.toDouble() / totalMeasuredSeconds
            } else {
                null
            },
            totalDowntimeSeconds = historicalDowntimeSeconds,
        )
    }

    /**
     * Calculates the uptime ratio and the daily status history of the given monitors over the given period, from a
     * single fetch of their uptime events, because both of the figures are derived from the very same data set.
     */
    fun calculateUptimeOverviews(
        monitorType: MonitorType,
        period: Duration,
        monitorIds: List<Long>,
    ): Map<Long, UptimeOverview> {
        if (monitorIds.isEmpty()) return emptyMap()
        // The whole batch is anchored to a single instant, so the overviews of the individual monitors stay comparable
        val now = getCurrentTimestamp()
        val eventsByMonitor = fetchUptimeEventsInPeriod(monitorType, period, monitorIds).groupBy { it.monitorId }

        return monitorIds.associateWith { monitorId ->
            val uptimeEvents = eventsByMonitor[monitorId].orEmpty()

            UptimeOverview(
                uptimeRatio = calculateHistoricalUptimeStats(period, uptimeEvents, now).uptimeRatio,
                statusHistory = generateUptimeHistoryOverview(period, uptimeEvents, now),
            )
        }
    }

    /**
     * Generates a list of daily uptime status history over a specified period based on uptime events, by summarizing
     * the number of outages (DOWN events) for each day. It returns an entry for each day in the period, even if there
     * were no events on that day.
     *
     * @param period The duration over which to generate the history.
     * @param uptimeEvents A list of uptime events to analyze.
     * @param now The instant both ends of the period are anchored to.
     *
     * @return A list of [StatusHistoryDto] representing the daily uptime status history.
     */
    private fun generateUptimeHistoryOverview(
        period: Duration,
        uptimeEvents: List<UptimeEventCalculationContext>,
        now: OffsetDateTime = getCurrentTimestamp(),
    ): List<StatusHistoryDto> {
        val periodStartTimestamp: OffsetDateTime = now.minus(period)
        val periodEnd: LocalDate = now.toLocalDate()
        // The start date is the current date minus the period, plus one day to include today as well
        val periodStart: LocalDate = periodStartTimestamp.toLocalDate().plusDays(1)
        val result = mutableListOf<StatusHistoryDto>()

        // Iterate over the days in the period and count the DOWN events for each day
        var processedDate = periodStart
        while (processedDate <= periodEnd) {
            val eventsEffectiveOnDate = uptimeEvents.filter { it.wasEffectiveOnDate(processedDate) }

            val historyEntry = if (eventsEffectiveOnDate.isEmpty()) {
                // If there are no events for the given date then we add a null entry
                StatusHistoryDto(
                    date = processedDate,
                    outageCnt = null,
                )
            } else {
                // Count the number of DOWN events effective on this date
                StatusHistoryDto(
                    date = processedDate,
                    outageCnt = eventsEffectiveOnDate.count { it.status == UptimeStatus.DOWN },
                )
            }
            result.add(historyEntry)
            processedDate = processedDate.plusDays(1)
        }

        return result
    }

    private fun fetchUptimeEventsInPeriod(
        monitorType: MonitorType,
        period: Duration,
        monitorIds: List<Long>,
    ): List<UptimeEventCalculationContext> =
        uptimeEventReposByType.getValue(monitorType).fetchAllInPeriod(period, monitorIds)

    private data class OverallStats(
        val monitors: List<MonitorDetailsDto>,
        val uptimeStats: ActualUptimeStats,
        val historicalUptimeStats: HistoricalUptimeStatsDto,
    )
}

/**
 * The uptime related figures of a single monitor over a period, calculated together from the same events.
 */
data class UptimeOverview(
    val uptimeRatio: Double?,
    val statusHistory: List<StatusHistoryDto>,
)

data class UptimeEventCalculationContext(
    val monitorId: Long,
    val isMonitorEnabled: Boolean,
    val status: UptimeStatus,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime,
) {
    fun effectiveStartDate(limitDate: OffsetDateTime): OffsetDateTime = maxOf(startedAt, limitDate)

    fun wasEffectiveOnDate(date: LocalDate): Boolean {
        val startDate = startedAt.toLocalDate()
        val endDate = endedAt?.toLocalDate() ?: updatedAt.toLocalDate()
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }
}
