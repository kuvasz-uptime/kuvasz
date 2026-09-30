package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorTypeUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorId
import com.kuvaszuptime.kuvasz.models.dto.monitor.monitorsWithCategory
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
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
import kotlin.math.ceil

@Singleton
class StatCalculator(
    monitorRepositories: List<MonitorRepository<*, *>>,
    uptimeEventRepositories: List<UptimeEventRepository>,
    private val maintenanceWindowService: MaintenanceWindowService,
) {
    private val monitorReposByType = monitorRepositories.associateBy { it.monitorType }
    private val uptimeEventReposByType = uptimeEventRepositories.associateBy { it.monitorType }

    companion object {
        // An hour-long period is sliced into 2.5-minute slots, a day-long one into hourly ones
        private const val SHORT_PERIOD_TIMELINE_SLOTS = 24L
        private val MID_PERIOD: Duration = Duration.ofDays(7)
        private const val MID_PERIOD_TIMELINE_SLOT_HOURS = 6L
        private const val MAX_TIMELINE_SLOTS = 60L
        // The events are measured in whole seconds, so shorter slots wouldn't make any sense
        private val MIN_TIMELINE_SLOT: Duration = Duration.ofSeconds(1)
        private val CERTIFICATE_ISSUES = setOf(SslStatus.INVALID, SslStatus.WILL_EXPIRE)
        const val LEAST_RELIABLE_MONITORS_LIMIT = 5
    }

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

    fun calculateOverallDockerStats(period: Duration): DockerMonitoringStatsDto {
        val overallStats = calculateOverallStats(MonitorType.DOCKER, period)

        return DockerMonitoringStatsDto(
            actual = DockerMonitoringStatsDto.ActualMonitoringStats(uptimeStats = overallStats.uptimeStats),
            history = DockerMonitoringStatsDto.HistoricalMonitoringStats(
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
    private fun calculateOverallStats(
        monitorType: MonitorType,
        period: Duration,
        now: OffsetDateTime = getCurrentTimestamp(),
    ): OverallStats {
        val monitors = monitorReposByType.getValue(monitorType).fetchAllWithDetails()
        val uptimeEventRepository = uptimeEventReposByType.getValue(monitorType)
        val uptimeEvents = uptimeEventRepository.fetchAllInPeriod(period)
        val windowsByMonitor = maintenanceWindowService.getWindowsForMonitors(
            monitorsWithCategory = monitors.filter { it.enabled }.monitorsWithCategory()
        )
        var downMonitors = 0
        var upMonitors = 0
        var pausedMonitors = 0
        var uptimeInProgressMonitors = 0
        var downMonitorsInMaintenance = 0
        val monitorsInMaintenance = mutableSetOf<NumericMonitorID>()

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
                monitorsInMaintenance.add(NumericMonitorID(monitorType, monitor.id))
                if (monitor.uptimeStatus == UptimeStatus.DOWN) downMonitorsInMaintenance++
            }
        }

        return OverallStats(
            monitors = monitors,
            uptimeEvents = uptimeEvents,
            monitorsInMaintenance = monitorsInMaintenance,
            downMonitorsInMaintenance = downMonitorsInMaintenance,
            uptimeStats = ActualUptimeStats(
                total = monitors.size,
                down = downMonitors,
                up = upMonitors,
                paused = pausedMonitors,
                inProgress = uptimeInProgressMonitors,
                inMaintenance = monitorsInMaintenance.size,
                lastIncident = uptimeEventRepository.fetchLatestIncidentTimestamp(),
            ),
            historicalUptimeStats = calculateHistoricalUptimeStats(period, uptimeEvents, now),
        )
    }

    /**
     * Calculates the uptime figures of the dashboard: the overall stats of every monitor type merged together, their
     * per-type breakdown, and a timeline of the whole period sliced into consecutive slots.
     */
    fun calculateDashboardUptimeStats(period: Duration): DashboardUptimeStats {
        // Every type is anchored to the same instant, otherwise their figures couldn't be merged
        val now = getCurrentTimestamp()
        val statsByType = MonitorType.entries.associateWith { calculateOverallStats(it, period, now) }
        val allUptimeEvents = statsByType.values.flatMap { it.uptimeEvents }
        val httpMonitors = statsByType.getValue(MonitorType.HTTP_SSL).monitors.filterIsInstance<HttpMonitorDetailsDto>()

        return DashboardUptimeStats(
            actual = statsByType.values.map { it.uptimeStats }.merge(),
            history = calculateHistoricalUptimeStats(period, allUptimeEvents, now).copy(
                // Monitor IDs are only unique within a type, so the merged events can't tell the monitors apart
                affectedMonitors = statsByType.values.sumOf { it.historicalUptimeStats.affectedMonitors },
            ),
            incidents = calculateIncidentStats(allUptimeEvents, periodStart = now.minus(period)),
            downInMaintenance = statsByType.values.sumOf { it.downMonitorsInMaintenance },
            sslStats = calculateSslStats(httpMonitors),
            timeline = generateUptimeTimeline(period, allUptimeEvents, now),
            byType = statsByType
                .filterValues { it.uptimeStats.total > 0 }
                .map { (type, stats) ->
                    MonitorTypeUptimeStats(
                        type = type,
                        actual = stats.uptimeStats,
                        history = stats.historicalUptimeStats,
                        timeline = generateUptimeTimeline(period, stats.uptimeEvents, now),
                    )
                },
            certificatesWithIssues = httpMonitors
                .filter { it.enabled && it.sslCheckEnabled && it.sslStatus in CERTIFICATE_ISSUES }
                .sortedWith(compareBy({ it.sslStatus != SslStatus.INVALID }, { it.sslValidUntil })),
            monitorsInMaintenance = statsByType.values.flatMap { it.monitorsInMaintenance }.toSet(),
            leastReliableMonitors = statsByType
                .flatMap { (type, stats) -> stats.unreliableMonitors(type, period, now) }
                .sortedWith(
                    compareByDescending<UnreliableMonitor> { it.history.totalDowntimeSeconds }
                        .thenByDescending { it.history.incidents }
                        .thenBy { it.name }
                )
                .take(LEAST_RELIABLE_MONITORS_LIMIT),
        )
    }

    /**
     * The monitors of the type that went down at least once during the period, with their own figures of the period.
     */
    private fun OverallStats.unreliableMonitors(
        type: MonitorType,
        period: Duration,
        now: OffsetDateTime,
    ): List<UnreliableMonitor> {
        val eventsByMonitor = uptimeEvents.groupBy { it.monitorId }

        return monitors.mapNotNull { monitor ->
            val history = eventsByMonitor[monitor.id]
                ?.let { calculateHistoricalUptimeStats(period, it, now) }
                ?.takeIf { it.incidents > 0 }
                ?: return@mapNotNull null
            val monitorId = NumericMonitorID(type, monitor.id)

            UnreliableMonitor(
                id = monitorId,
                name = monitor.name,
                enabled = monitor.enabled,
                uptimeStatus = monitor.uptimeStatus,
                inMaintenance = monitorId in monitorsInMaintenance,
                history = history,
            )
        }
    }

    private fun List<ActualUptimeStats>.merge() = ActualUptimeStats(
        total = sumOf { it.total },
        down = sumOf { it.down },
        up = sumOf { it.up },
        paused = sumOf { it.paused },
        inProgress = sumOf { it.inProgress },
        inMaintenance = sumOf { it.inMaintenance },
        lastIncident = mapNotNull { it.lastIncident }.maxOrNull(),
    )

    /**
     * Counts the incidents of the period, the same ones as [calculateHistoricalUptimeStats] does, by their state.
     */
    private fun calculateIncidentStats(
        uptimeEvents: List<UptimeEventCalculationContext>,
        periodStart: OffsetDateTime,
    ): DashboardIncidentStats {
        val incidents = uptimeEvents.filter { it.status == UptimeStatus.DOWN && it.wasEffectiveSince(periodStart) }
        // The ones of the paused monitors that were down when they got paused are neither ongoing, nor resolved
        val resolvedIncidents = incidents.mapNotNull { incident ->
            incident.endedAt?.let { Duration.between(incident.startedAt, it).seconds }
        }

        return DashboardIncidentStats(
            ongoing = incidents.count { it.endedAt == null && it.isMonitorEnabled },
            resolved = resolvedIncidents.size,
            meanTimeToResolveSeconds = resolvedIncidents.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        )
    }

    /**
     * Slices the period into consecutive slots (the last one ends at [now]) and sums up how much uptime and downtime
     * the given events contributed to each of them.
     */
    private fun generateUptimeTimeline(
        period: Duration,
        uptimeEvents: List<UptimeEventCalculationContext>,
        now: OffsetDateTime,
    ): List<UptimeTimelineSlot> {
        val periodStart = now.minus(period)
        val slotLength = timelineSlotLength(period)
        val slotCount = ceil(period.toMillis().toDouble() / slotLength.toMillis()).toInt()
        val effectiveEvents = uptimeEvents.filter { it.wasEffectiveSince(periodStart) }
        // Only counted in the slot where they started, otherwise a long outage would show up in every slot it spans.
        // The ones that started before the period are counted in the first slot, so the slots add up to the history.
        val incidentsBySlot = effectiveEvents
            .filter { it.status == UptimeStatus.DOWN }
            .groupingBy { incident ->
                Duration.between(periodStart, incident.effectiveStartDate(periodStart))
                    .dividedBy(slotLength)
                    .toInt()
                    .coerceAtMost(slotCount - 1)
            }
            .eachCount()

        return (0 until slotCount).map { slotIndex ->
            val slotStart = periodStart.plus(slotLength.multipliedBy(slotIndex.toLong()))
            val slotEnd = minOf(slotStart.plus(slotLength), now)
            var uptimeSeconds = 0L
            var downtimeSeconds = 0L

            effectiveEvents.forEach { uptimeEvent ->
                val overlapSeconds = Duration.between(
                    maxOf(uptimeEvent.startedAt, slotStart),
                    minOf(uptimeEvent.effectiveEndDate(now), slotEnd),
                ).seconds
                if (overlapSeconds <= 0) return@forEach

                when (uptimeEvent.status) {
                    UptimeStatus.UP -> uptimeSeconds += overlapSeconds
                    UptimeStatus.DOWN -> downtimeSeconds += overlapSeconds
                }
            }
            UptimeTimelineSlot(slotStart, slotEnd, uptimeSeconds, downtimeSeconds, incidentsBySlot[slotIndex] ?: 0)
        }
    }

    private fun timelineSlotLength(period: Duration): Duration = when {
        period <= Duration.ofDays(1) -> maxOf(MIN_TIMELINE_SLOT, period.dividedBy(SHORT_PERIOD_TIMELINE_SLOTS))
        period <= MID_PERIOD -> Duration.ofHours(MID_PERIOD_TIMELINE_SLOT_HOURS)
        else -> maxOf(Duration.ofDays(1), period.dividedBy(MAX_TIMELINE_SLOTS))
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
            if (!uptimeEvent.wasEffectiveSince(periodStart)) {
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
        val uptimeEvents: List<UptimeEventCalculationContext>,
        val monitorsInMaintenance: Set<NumericMonitorID>,
        val downMonitorsInMaintenance: Int,
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

    /** An event of a paused monitor is only effective until its last update, see [getDurationOfEvent]. */
    fun wasEffectiveSince(periodStart: OffsetDateTime): Boolean = isMonitorEnabled || !updatedAt.isBefore(periodStart)

    /** The last instant the event was effective, see [getDurationOfEvent]. */
    fun effectiveEndDate(now: OffsetDateTime): OffsetDateTime = endedAt ?: if (isMonitorEnabled) now else updatedAt

    fun wasEffectiveOnDate(date: LocalDate): Boolean {
        val startDate = startedAt.toLocalDate()
        val endDate = endedAt?.toLocalDate() ?: updatedAt.toLocalDate()
        return !date.isBefore(startDate) && !date.isAfter(endDate)
    }
}
