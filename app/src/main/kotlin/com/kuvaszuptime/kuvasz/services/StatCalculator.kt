package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorStateCounts
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorTypeUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
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
import java.time.ZoneId

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
        const val CERTIFICATES_WITH_ISSUES_LIMIT = 5
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
     *
     * @param excludePausedMonitors Whether the history should leave out the events of the monitors that are paused now,
     * even if they were checked during a part of the period.
     * @param maintenanceWindows Every maintenance window, if they're already at hand, so the ones affecting the
     * monitors are matched in memory, instead of being resolved for every monitor on the database side.
     */
    private fun calculateOverallStats(
        monitorType: MonitorType,
        period: Duration,
        now: OffsetDateTime = getCurrentTimestamp(),
        excludePausedMonitors: Boolean = false,
        maintenanceWindows: List<MaintenanceWindowDetailsDto>? = null,
    ): OverallStats {
        val monitors = monitorReposByType.getValue(monitorType).fetchSummaries()
        val uptimeEventRepository = uptimeEventReposByType.getValue(monitorType)
        // A type without any monitor can't have any events either, so they aren't even fetched
        val uptimeEvents = if (monitors.isEmpty()) {
            emptyList()
        } else {
            uptimeEventRepository.fetchAllInPeriod(
                periodStart = now.minus(period),
                periodEnd = now,
                onlyEnabledMonitors = excludePausedMonitors,
            )
        }
        val enabledMonitors = monitors.filter { it.enabled }
        val windowsByMonitor = enabledMonitors.maintenanceWindowsByMonitor(maintenanceWindows)
        val (maintainedMonitors, monitorsOutsideMaintenance) = enabledMonitors.partition { monitor ->
            windowsByMonitor[monitor.monitorId]?.any { it.active } == true
        }
        val states = enabledMonitors.stateCounts()

        return OverallStats(
            monitors = monitors,
            uptimeEvents = uptimeEvents,
            monitorsInMaintenance = maintainedMonitors.map { NumericMonitorID(monitorType, it.id) }.toSet(),
            statesOutsideMaintenance = monitorsOutsideMaintenance.stateCounts(),
            statesInMaintenance = maintainedMonitors.stateCounts(),
            uptimeStats = ActualUptimeStats(
                total = monitors.size,
                down = states.down,
                up = states.up,
                paused = monitors.size - enabledMonitors.size,
                inProgress = states.pending,
                inMaintenance = maintainedMonitors.size,
                lastIncident = if (monitors.isEmpty()) null else uptimeEventRepository.fetchLatestIncidentTimestamp(),
            ),
            historicalUptimeStats = calculateHistoricalUptimeStats(period, uptimeEvents, now),
        )
    }

    /**
     * The maintenance windows affecting the given monitors. If every window is already at hand, the active ones are
     * matched in memory, instead of resolving the windows of every monitor on the database side.
     */
    private fun List<MonitorSummary>.maintenanceWindowsByMonitor(
        maintenanceWindows: List<MaintenanceWindowDetailsDto>?,
    ): Map<MonitorID, List<MaintenanceWindowDetailsDto>> {
        if (maintenanceWindows == null) {
            return maintenanceWindowService.getWindowsForMonitors(associate { it.monitorId to it.category })
        }
        val activeWindows = maintenanceWindows.filter { it.enabled && it.active }
        return associate { monitor -> monitor.monitorId to activeWindows.filter { it.affects(monitor) } }
    }

    /**
     * Calculates the uptime figures of the dashboard: the overall stats of every monitor type merged together, their
     * per-type breakdown, and a timeline of the whole period sliced into consecutive slots. Just like the incidents
     * listed on the dashboard, the figures only cover the monitors that aren't paused.
     */
    fun calculateDashboardUptimeStats(
        period: Duration,
        maintenanceWindows: List<MaintenanceWindowDetailsDto>,
    ): DashboardUptimeStats {
        // Every type is anchored to the same instant, otherwise their figures couldn't be merged
        val now = getCurrentTimestamp()
        val statsByType = MonitorType.entries.associateWith {
            calculateOverallStats(it, period, now, excludePausedMonitors = true, maintenanceWindows)
        }
        val allUptimeEvents = statsByType.values.flatMap { it.uptimeEvents }
        val timelinesByType = statsByType.mapValues { (_, stats) ->
            generateUptimeTimeline(period, stats.uptimeEvents, now)
        }
        val httpMonitors = statsByType.getValue(MonitorType.HTTP_SSL).monitors.filterIsInstance<HttpMonitorSummary>()

        return DashboardUptimeStats(
            actual = statsByType.values.map { it.uptimeStats }.merge(),
            history = calculateHistoricalUptimeStats(period, allUptimeEvents, now).copy(
                // Monitor IDs are only unique within a type, so the merged events can't tell the monitors apart
                affectedMonitors = statsByType.values.sumOf { it.historicalUptimeStats.affectedMonitors },
            ),
            incidents = calculateIncidentStats(statsByType),
            outsideMaintenance = statsByType.values.map { it.statesOutsideMaintenance }.merge(),
            inMaintenance = statsByType.values.map { it.statesInMaintenance }.merge(),
            sslStats = calculateSslStats(httpMonitors),
            timeline = timelinesByType.values.merge(),
            byType = statsByType
                .filterValues { it.uptimeStats.total > 0 }
                .map { (type, stats) ->
                    MonitorTypeUptimeStats(
                        type = type,
                        actual = stats.uptimeStats,
                        outsideMaintenance = stats.statesOutsideMaintenance,
                        inMaintenance = stats.statesInMaintenance,
                        history = stats.historicalUptimeStats,
                        timeline = timelinesByType.getValue(type),
                    )
                },
            certificatesWithIssues = httpMonitors
                .filter { it.enabled && it.sslCheckEnabled && it.sslStatus in CERTIFICATE_ISSUES }
                .sortedWith(compareBy({ it.sslStatus != SslStatus.INVALID }, { it.sslValidUntil }))
                .take(CERTIFICATES_WITH_ISSUES_LIMIT),
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

    private fun List<MonitorSummary>.stateCounts() = MonitorStateCounts(
        up = count { it.uptimeStatus == UptimeStatus.UP },
        down = count { it.uptimeStatus == UptimeStatus.DOWN },
        pending = count { it.uptimeStatus == null },
    )

    /** Every timeline is expected to have the same slots, i.e. to be generated for the same period and instant. */
    @JvmName("mergeTimelines")
    private fun Collection<List<UptimeTimelineSlot>>.merge() = reduce { merged, timeline ->
        merged.zip(timeline) { mergedSlot, slot ->
            mergedSlot.copy(
                uptimeSeconds = mergedSlot.uptimeSeconds + slot.uptimeSeconds,
                downtimeSeconds = mergedSlot.downtimeSeconds + slot.downtimeSeconds,
                incidents = mergedSlot.incidents + slot.incidents,
            )
        }
    }

    @JvmName("mergeStateCounts")
    private fun List<MonitorStateCounts>.merge() = MonitorStateCounts(
        up = sumOf { it.up },
        down = sumOf { it.down },
        pending = sumOf { it.pending },
    )

    /**
     * Counts the incidents of the period, the same ones as [calculateHistoricalUptimeStats] does, by their state.
     * Only the events of the monitors that aren't paused are expected, see [calculateDashboardUptimeStats].
     */
    private fun calculateIncidentStats(statsByType: Map<MonitorType, OverallStats>): DashboardIncidentStats {
        val incidentsByType = statsByType.mapValues { (_, stats) ->
            stats.uptimeEvents.filter { it.status == UptimeStatus.DOWN }
        }
        val incidents = incidentsByType.values.flatten()
        val resolvedIncidents = incidents.mapNotNull { incident ->
            incident.endedAt?.let { Duration.between(incident.startedAt, it).seconds }
        }
        // Monitor IDs are only unique within a type, so the maintenance of the monitors is looked up type by type
        val ongoingInMaintenance = incidentsByType.entries.sumOf { (type, typeIncidents) ->
            typeIncidents.count { incident ->
                incident.endedAt == null &&
                    NumericMonitorID(type, incident.monitorId) in statsByType.getValue(type).monitorsInMaintenance
            }
        }

        return DashboardIncidentStats(
            ongoingOutsideMaintenance = incidents.count { it.endedAt == null } - ongoingInMaintenance,
            ongoingInMaintenance = ongoingInMaintenance,
            resolved = resolvedIncidents.size,
            meanTimeToResolveSeconds = resolvedIncidents.takeIf { it.isNotEmpty() }?.average()?.toLong(),
        )
    }

    /**
     * Slices the period into consecutive slots (the last one ends at [now], see [timelineSlotStarts]) and sums up how
     * much uptime and downtime the given events contributed to each of them. Only the events of the monitors that
     * aren't paused are expected, see [calculateDashboardUptimeStats].
     */
    private fun generateUptimeTimeline(
        period: Duration,
        uptimeEvents: List<UptimeEventCalculationContext>,
        now: OffsetDateTime,
    ): List<UptimeTimelineSlot> {
        val periodStart = now.minus(period)
        val slotStarts = timelineSlotStarts(periodStart, now, timelineSlotLength(period))
        val slotEnds = slotStarts.drop(1) + now
        val slotCount = slotStarts.size
        val uptimeSeconds = LongArray(slotCount)
        val downtimeSeconds = LongArray(slotCount)
        val incidents = IntArray(slotCount)

        fun slotIndexOf(instant: OffsetDateTime): Int {
            val index = slotStarts.binarySearch { it.toInstant().compareTo(instant.toInstant()) }
            // Not being a start itself, the instant is in the slot starting right before it
            return (if (index >= 0) index else -index - 2).coerceIn(0, slotCount - 1)
        }

        uptimeEvents.forEach { uptimeEvent ->
            val start = uptimeEvent.effectiveStartDate(periodStart)
            val end = minOf(uptimeEvent.effectiveEndDate(now), now)
            // Only counted in the slot where they started, otherwise a long outage would show up in every slot it
            // spans. The ones that started before the period are counted in the first slot, so the slots add up to
            // the history.
            if (uptimeEvent.status == UptimeStatus.DOWN) incidents[slotIndexOf(start)]++
            if (!end.isAfter(start)) return@forEach

            // Only the slots the event overlaps are visited
            for (slotIndex in slotIndexOf(start)..slotIndexOf(end)) {
                val overlapSeconds = Duration.between(
                    maxOf(start, slotStarts[slotIndex]),
                    minOf(end, slotEnds[slotIndex]),
                ).seconds
                if (overlapSeconds <= 0) continue

                when (uptimeEvent.status) {
                    UptimeStatus.UP -> uptimeSeconds[slotIndex] += overlapSeconds
                    UptimeStatus.DOWN -> downtimeSeconds[slotIndex] += overlapSeconds
                }
            }
        }

        return (0 until slotCount).map { slotIndex ->
            UptimeTimelineSlot(
                start = slotStarts[slotIndex],
                end = slotEnds[slotIndex],
                uptimeSeconds = uptimeSeconds[slotIndex],
                downtimeSeconds = downtimeSeconds[slotIndex],
                incidents = incidents[slotIndex],
            )
        }
    }

    /**
     * The starts of the slots of the timeline: the start of the period, then every boundary after it. The boundaries
     * are aligned to the local midnight (see [ZoneId.systemDefault]), so the slots don't shift between two refreshes of
     * the dashboard, and a daily one is a calendar day. Only the first and the current slot can be shorter than the
     * [slotLength].
     */
    private fun timelineSlotStarts(
        periodStart: OffsetDateTime,
        now: OffsetDateTime,
        slotLength: Duration,
    ): List<OffsetDateTime> {
        val zone = ZoneId.systemDefault()
        val boundaries = generateSequence(periodStart.atZoneSameInstant(zone).toLocalDate().atStartOfDay()) {
            it.plus(slotLength)
        }
            .map { it.atZone(zone).toOffsetDateTime() }
            .dropWhile { !it.isAfter(periodStart) }
            .takeWhile { it.isBefore(now) }

        // A local time skipped by a DST change maps to the same instant as the next one
        return (sequenceOf(periodStart) + boundaries).distinctBy { it.toInstant() }.toList()
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
        monitors: List<MonitorSummary>,
    ): HttpMonitoringStatsDto.ActualMonitoringStats.SslStats {
        var validMonitors = 0
        var invalidMonitors = 0
        var willExpireMonitors = 0
        var inProgressMonitors = 0

        monitors
            .filterIsInstance<HttpMonitorSummary>()
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
    ): HistoricalUptimeStatsDto {
        val now = getCurrentTimestamp()
        return calculateHistoricalUptimeStats(
            period = period,
            uptimeEvents = fetchUptimeEventsInPeriod(monitorType, period, listOf(monitorId), now),
            now = now,
        )
    }

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
        val eventsByMonitor = fetchUptimeEventsInPeriod(monitorType, period, monitorIds, now).groupBy { it.monitorId }

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
        now: OffsetDateTime,
    ): List<UptimeEventCalculationContext> =
        uptimeEventReposByType.getValue(monitorType).fetchAllInPeriod(now.minus(period), now, monitorIds)

    private data class OverallStats(
        val monitors: List<MonitorSummary>,
        val uptimeEvents: List<UptimeEventCalculationContext>,
        val monitorsInMaintenance: Set<NumericMonitorID>,
        val statesOutsideMaintenance: MonitorStateCounts,
        val statesInMaintenance: MonitorStateCounts,
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

// The same rules as the ones resolving the windows of the monitors on the database side
private fun MaintenanceWindowDetailsDto.affects(monitor: MonitorSummary): Boolean =
    global || monitor.monitorId in monitors || monitor.category?.let { it in categories } == true
