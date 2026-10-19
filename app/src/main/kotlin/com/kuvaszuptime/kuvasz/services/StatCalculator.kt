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
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
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
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentNoteDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import com.kuvaszuptime.kuvasz.models.uptimeIncidentType
import com.kuvaszuptime.kuvasz.repositories.IncidentNoteRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.monitorType
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowService
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import com.kuvaszuptime.kuvasz.util.getEffectiveEndOfEvent
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Singleton
class StatCalculator(
    monitorRepositories: List<MonitorRepository<*, *>>,
    uptimeEventRepositories: List<UptimeEventRepository>,
    private val maintenanceWindowService: MaintenanceWindowService,
    private val incidentNoteRepository: IncidentNoteRepository,
    dispatcher: CoroutineDispatcher,
) {
    private val monitorReposByType = monitorRepositories.associateBy { it.monitorType }
    private val uptimeEventReposByType = uptimeEventRepositories.associateBy { it.monitorType }

    // Shared by every request, so the dashboards can't take up the connections of the checks
    private val dashboardDispatcher = dispatcher.limitedParallelism(MAX_PARALLEL_DASHBOARD_QUERIES)

    companion object {
        // An hour-long period is sliced into 2.5-minute slots, a day-long one into hourly ones
        private const val SHORT_PERIOD_TIMELINE_SLOTS = 24L
        private val MID_PERIOD: Duration = Duration.ofDays(7)
        private const val MID_PERIOD_TIMELINE_SLOT_HOURS = 6L
        private const val MAX_TIMELINE_SLOTS = 60L

        // The events are measured in whole seconds, so shorter slots wouldn't make any sense
        private val MIN_TIMELINE_SLOT: Duration = Duration.ofSeconds(1)

        // Longer than any shift of the clocks by a DST change
        private val MAX_DST_SHIFT: Duration = Duration.ofHours(2)
        private val CERTIFICATE_ISSUES = setOf(SslStatus.INVALID, SslStatus.WILL_EXPIRE)
        const val LEAST_RELIABLE_MONITORS_LIMIT = 5
        const val CERTIFICATES_WITH_ISSUES_LIMIT = 5
        private const val MAX_PARALLEL_DASHBOARD_QUERIES = 4
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
        now: OffsetDateTime = currentInstant(),
        excludePausedMonitors: Boolean = false,
        maintenanceWindows: List<MaintenanceWindowDetailsDto>? = null,
    ): OverallStats {
        val monitors = monitorReposByType.getValue(monitorType).fetchSummaries()
        val uptimeEventRepository = uptimeEventReposByType.getValue(monitorType)
        val enabledMonitors = monitors.filter { it.enabled }
        val windowsByMonitor = enabledMonitors.maintenanceWindowsByMonitor(maintenanceWindows)
        val (maintainedMonitors, monitorsOutsideMaintenance) = enabledMonitors.partition { monitor ->
            windowsByMonitor[monitor.monitorId]?.any { it.active } == true
        }
        // A type without any monitor can't have any events either, so they aren't even added up
        val uptimeAggregate = if (monitors.isEmpty()) {
            UptimeAggregate.EMPTY
        } else {
            uptimeEventRepository.aggregateInPeriod(
                periodStart = now.minus(period),
                periodEnd = now,
                onlyEnabledMonitors = excludePausedMonitors,
                monitorIdsInMaintenance = maintainedMonitors.map { it.id },
            )
        }
        val statesOutsideMaintenance = monitorsOutsideMaintenance.stateCounts()
        val statesInMaintenance = maintainedMonitors.stateCounts()
        val states = listOf(statesOutsideMaintenance, statesInMaintenance).merge()

        return OverallStats(
            monitors = monitors,
            uptimeAggregate = uptimeAggregate,
            monitorsInMaintenance = maintainedMonitors.map { NumericMonitorID(monitorType, it.id) }.toSet(),
            statesOutsideMaintenance = statesOutsideMaintenance,
            statesInMaintenance = statesInMaintenance,
            uptimeStats = ActualUptimeStats(
                total = monitors.size,
                down = states.down,
                up = states.up,
                paused = monitors.size - enabledMonitors.size,
                inProgress = states.pending,
                inMaintenance = maintainedMonitors.size,
                lastIncident = if (monitors.isEmpty()) null else uptimeEventRepository.fetchLatestIncidentTimestamp(),
            ),
            historicalUptimeStats = uptimeAggregate.toHistoricalUptimeStats(period),
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
    ): DashboardUptimeStats = calculateDashboardUptimeStats(period, maintenanceWindows, currentInstant())

    /**
     * See the other [calculateDashboardUptimeStats]. Every type is anchored to the same instant, [now], otherwise their
     * figures couldn't be merged.
     */
    internal fun calculateDashboardUptimeStats(
        period: Duration,
        maintenanceWindows: List<MaintenanceWindowDetailsDto>,
        now: OffsetDateTime,
    ): DashboardUptimeStats {
        // Every type's timeline has the same slots, so they can be merged slot by slot
        val slotStarts = timelineSlotStarts(period, now)
        val figuresByType = calculateDashboardFiguresOfEveryType(period, now, slotStarts, maintenanceWindows)
        val statsByType = figuresByType.mapValues { (_, figures) -> figures.overallStats }
        val uptimeAggregates = statsByType.values.map { it.uptimeAggregate }
        val httpMonitors = statsByType.getValue(MonitorType.HTTP_SSL).monitors.filterIsInstance<HttpMonitorSummary>()

        return DashboardUptimeStats(
            actual = statsByType.values.map { it.uptimeStats }.merge(),
            // Monitor IDs are only unique within a type, so the affected monitors are counted type by type
            history = uptimeAggregates.merge().toHistoricalUptimeStats(period),
            incidents = calculateIncidentStats(uptimeAggregates),
            outsideMaintenance = statsByType.values.map { it.statesOutsideMaintenance }.merge(),
            inMaintenance = statsByType.values.map { it.statesInMaintenance }.merge(),
            sslStats = calculateSslStats(httpMonitors),
            timeline = figuresByType.values.map { it.timeline }.merge(),
            byType = figuresByType
                .filterValues { it.overallStats.uptimeStats.total > 0 }
                .map { (type, figures) ->
                    MonitorTypeUptimeStats(
                        type = type,
                        actual = figures.overallStats.uptimeStats,
                        outsideMaintenance = figures.overallStats.statesOutsideMaintenance,
                        inMaintenance = figures.overallStats.statesInMaintenance,
                        history = figures.overallStats.historicalUptimeStats,
                        timeline = figures.timeline,
                    )
                },
            certificatesWithIssues = httpMonitors
                .filter { it.enabled && it.sslCheckEnabled && it.sslStatus in CERTIFICATE_ISSUES }
                .sortedWith(compareBy({ it.sslStatus != SslStatus.INVALID }, { it.sslValidUntil }))
                .take(CERTIFICATES_WITH_ISSUES_LIMIT),
            monitorsInMaintenance = statsByType.values.flatMap { it.monitorsInMaintenance }.toSet(),
            leastReliableMonitors = figuresByType.values
                .flatMap { it.leastReliableMonitors }
                .sortedWith(
                    compareByDescending<UnreliableMonitor> { it.history.totalDowntimeSeconds }
                        .thenByDescending { it.history.incidents }
                        .thenBy { it.name }
                )
                .take(LEAST_RELIABLE_MONITORS_LIMIT),
        )
    }

    /** The dashboard figures of every type, calculated in parallel, without the monitors that are paused. */
    private fun calculateDashboardFiguresOfEveryType(
        period: Duration,
        now: OffsetDateTime,
        slotStarts: List<OffsetDateTime>,
        maintenanceWindows: List<MaintenanceWindowDetailsDto>,
    ): Map<MonitorType, DashboardFigures> = runBlocking(dashboardDispatcher) {
        MonitorType.entries.map { type ->
            async {
                val overallStats = calculateOverallStats(
                    type,
                    period,
                    now,
                    excludePausedMonitors = true,
                    maintenanceWindows = maintenanceWindows,
                )
                type to DashboardFigures(
                    overallStats = overallStats,
                    timeline = overallStats.timeline(type, slotStarts, now),
                    leastReliableMonitors = overallStats.leastReliableMonitors(type, period, now),
                )
            }
        }.awaitAll().toMap()
    }

    /**
     * How much uptime and downtime the monitors of the type had in each slot of the period (the last one ends at
     * [now], see [timelineSlotStarts]), and how many incidents started in them.
     */
    private fun OverallStats.timeline(
        type: MonitorType,
        slotStarts: List<OffsetDateTime>,
        now: OffsetDateTime,
    ): List<UptimeTimelineSlot> {
        val slotEnds = slotStarts.drop(1) + now
        val slots = if (monitors.isEmpty()) {
            slotStarts.map { UptimeTimelineAggregate(uptimeSeconds = 0, downtimeSeconds = 0, incidents = 0) }
        } else {
            uptimeEventReposByType.getValue(type).aggregateTimeline(slotStarts, now, onlyEnabledMonitors = true)
        }
        return slots.mapIndexed { slotIndex, slot ->
            UptimeTimelineSlot(
                start = slotStarts[slotIndex],
                end = slotEnds[slotIndex],
                uptimeSeconds = slot.uptimeSeconds,
                downtimeSeconds = slot.downtimeSeconds,
                incidents = slot.incidents,
            )
        }
    }

    /**
     * The monitors of the type that went down at least once during the period, with their own figures of the period,
     * the ones with the most downtime first.
     */
    private fun OverallStats.leastReliableMonitors(
        type: MonitorType,
        period: Duration,
        now: OffsetDateTime,
    ): List<UnreliableMonitor> {
        if (monitors.isEmpty()) return emptyList()
        val monitorsById = monitors.associateBy { it.id }

        return uptimeEventReposByType.getValue(type)
            .fetchLeastReliableMonitors(
                periodStart = now.minus(period),
                periodEnd = now,
                limit = LEAST_RELIABLE_MONITORS_LIMIT,
                onlyEnabledMonitors = true,
            )
            .mapNotNull { uptime ->
                // A monitor created since its type's monitors were listed can't be shown without its details
                val monitor = monitorsById[uptime.monitorId] ?: return@mapNotNull null
                val monitorId = NumericMonitorID(type, monitor.id)
                UnreliableMonitor(
                    id = monitorId,
                    name = monitor.name,
                    uptimeStatus = monitor.uptimeStatus,
                    inMaintenance = monitorId in monitorsInMaintenance,
                    history = uptime.toHistoricalUptimeStats(period),
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

    @JvmName("mergeUptimeAggregates")
    private fun List<UptimeAggregate>.merge() = UptimeAggregate(
        uptimeSeconds = sumOf { it.uptimeSeconds },
        downtimeSeconds = sumOf { it.downtimeSeconds },
        incidents = sumOf { it.incidents },
        affectedMonitors = sumOf { it.affectedMonitors },
        ongoingIncidents = sumOf { it.ongoingIncidents },
        ongoingIncidentsInMaintenance = sumOf { it.ongoingIncidentsInMaintenance },
        resolvedIncidents = sumOf { it.resolvedIncidents },
        resolvedIncidentSeconds = sumOf { it.resolvedIncidentSeconds },
    )

    /**
     * Counts the incidents of the period, the same ones as the history does, by their state. Only the figures of the
     * monitors that aren't paused are expected, see [calculateDashboardUptimeStats].
     */
    private fun calculateIncidentStats(uptimeAggregates: List<UptimeAggregate>): DashboardIncidentStats {
        val merged = uptimeAggregates.merge()

        return DashboardIncidentStats(
            ongoingOutsideMaintenance = merged.ongoingIncidents - merged.ongoingIncidentsInMaintenance,
            ongoingInMaintenance = merged.ongoingIncidentsInMaintenance,
            resolved = merged.resolvedIncidents,
            meanTimeToResolveSeconds = merged.resolvedIncidentSeconds
                .takeIf { merged.resolvedIncidents > 0 }
                ?.let { (it.toDouble() / merged.resolvedIncidents).toLong() },
        )
    }

    /**
     * The starts of the slots of the timeline, in order: the start of the period, then every boundary after it. The
     * boundaries are aligned to the local midnight of the [zone], so the slots don't shift between two refreshes of the
     * dashboard, and a daily one is a calendar day. They are in the offset of the [zone] at their instant, so their
     * local times are the ones of the zone. Only the first and the current slot can be shorter than the slot length of
     * the period, and only the one that a DST change falls into can be longer or shorter.
     */
    internal fun timelineSlotStarts(
        period: Duration,
        now: OffsetDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<OffsetDateTime> {
        val periodStart = now.minus(period).atZoneSameInstant(zone).toOffsetDateTime()
        val slotLength = timelineSlotLength(period)
        // The local times only go forward, unlike their instants around a DST change: a skipped one is moved forward
        // onto the same instant as a later one, and a repeated one is a boundary on both of its offsets, the later of
        // which can come after the period start, even if the local time is before it. So they're stepped from a bit
        // before the period start to a bit past now, to be sure every boundary of the period is reached, then trimmed
        // and ordered by their instants.
        val firstLocalTime = periodStart.toLocalDateTime().minus(MAX_DST_SHIFT)
        val midnight = firstLocalTime.toLocalDate().atStartOfDay()
        val slotsSinceMidnight = Duration.between(midnight, firstLocalTime).dividedBy(slotLength)
        val firstBoundary = midnight.plus(slotLength.multipliedBy(slotsSinceMidnight))
        val lastLocalTime = now.atZoneSameInstant(zone).toLocalDateTime().plus(slotLength).plus(MAX_DST_SHIFT)
        val boundaries = generateSequence(firstBoundary) { it.plus(slotLength) }
            .takeWhile { !it.isAfter(lastLocalTime) }
            .map { it.atZone(zone) }
            .flatMap { sequenceOf(it.withEarlierOffsetAtOverlap(), it.withLaterOffsetAtOverlap()) }
            .map { it.toOffsetDateTime() }
            .filter { it.isAfter(periodStart) && it.isBefore(now) }

        return (sequenceOf(periodStart) + boundaries)
            .distinctBy { it.toInstant() }
            .sortedBy { it.toInstant() }
            .toList()
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
    ): HistoricalUptimeStatsDto = calculateHistoricalUptimeStats(monitorType, period, monitorId, currentInstant())

    /** See the other [calculateHistoricalUptimeStats], the period ending at [now]. */
    internal fun calculateHistoricalUptimeStats(
        monitorType: MonitorType,
        period: Duration,
        monitorId: Long,
        now: OffsetDateTime,
    ): HistoricalUptimeStatsDto =
        uptimeEventReposByType.getValue(monitorType)
            .aggregateInPeriod(periodStart = now.minus(period), periodEnd = now, monitorIds = listOf(monitorId))
            .toHistoricalUptimeStats(period)

    /**
     * Calculates the uptime ratio and the daily status history of the given monitors over the given period. Only the
     * figures and the incidents leave the database, not every event of the period. The incidents of the history carry
     * their public notes, as the overviews are made for the status pages.
     */
    fun calculateUptimeOverviews(
        monitorType: MonitorType,
        period: Duration,
        monitorIds: List<Long>,
    ): Map<Long, UptimeOverview> = calculateUptimeOverviews(monitorType, period, monitorIds, currentInstant())

    /**
     * See the other [calculateUptimeOverviews]. The whole batch is anchored to a single instant, [now], so the
     * overviews of the individual monitors stay comparable.
     */
    internal fun calculateUptimeOverviews(
        monitorType: MonitorType,
        period: Duration,
        monitorIds: List<Long>,
        now: OffsetDateTime,
    ): Map<Long, UptimeOverview> {
        if (monitorIds.isEmpty()) return emptyMap()
        val periodStart = now.minus(period)
        val uptimeEventRepository = uptimeEventReposByType.getValue(monitorType)
        val uptimeByMonitor = uptimeEventRepository.aggregateByMonitor(periodStart, now, monitorIds)
            .associateBy { it.monitorId }
        val days = statusHistoryDays(period, now)
        val daysWithEvents = if (days.isEmpty()) {
            emptyMap()
        } else {
            uptimeEventRepository.fetchDaysWithEvents(
                periodStart = periodStart,
                periodEnd = now,
                monitorIds = monitorIds,
                dayStarts = days.map { it.start },
                lastDayEnd = days.last().end,
            )
        }
        val incidents = uptimeEventRepository.fetchIncidentsInPeriod(periodStart, now, monitorIds)
        val publicNotesByEvent = incidentNoteRepository.fetchPublicByEvents(
            incidentType = monitorType.uptimeIncidentType,
            eventIds = incidents.map { it.id },
        )
        val incidentsByMonitor = incidents.groupBy { it.monitorId }

        return monitorIds.associateWith { monitorId ->
            UptimeOverview(
                uptimeRatio = uptimeByMonitor[monitorId]?.let { uptimeRatioOf(it.uptimeSeconds, it.downtimeSeconds) },
                statusHistory = generateUptimeHistoryOverview(
                    days = days,
                    daysWithEvents = daysWithEvents[monitorId].orEmpty(),
                    incidents = incidentsByMonitor[monitorId].orEmpty(),
                    publicNotesByEvent = publicNotesByEvent,
                    now = now,
                ),
            )
        }
    }

    /**
     * The days of the status history in the system's time zone: from the day after the start of the period, to the
     * current one.
     */
    private fun statusHistoryDays(period: Duration, now: OffsetDateTime): List<StatusHistoryDay> {
        val zone = ZoneId.systemDefault()
        return generateSequence(now.minus(period).toLocalDate().plusDays(1)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(now.toLocalDate()) }
            .map { date ->
                StatusHistoryDay(
                    date = date,
                    start = date.atStartOfDay(zone).toOffsetDateTime(),
                    end = date.plusDays(1).atStartOfDay(zone).toOffsetDateTime(),
                )
            }
            .toList()
    }

    /**
     * Summarizes the outages (incidents) of a monitor day by day: their number, the downtime they caused on that day,
     * and the incidents themselves. A day without any event in effect has no figures at all.
     */
    private fun generateUptimeHistoryOverview(
        days: List<StatusHistoryDay>,
        daysWithEvents: Set<Int>,
        incidents: List<UptimeEventCalculationContext>,
        publicNotesByEvent: Map<Long, List<StatusPageIncidentNoteDto>>,
        now: OffsetDateTime,
    ): List<StatusHistoryDto> = days.mapIndexed { dayIndex, day ->
        if (dayIndex !in daysWithEvents) {
            StatusHistoryDto(date = day.date, outageCnt = null)
        } else {
            val outages = incidents
                .filter { it.wasEffectiveBetween(day.start, day.end) }
                .sortedBy { it.startedAt }
            StatusHistoryDto(
                date = day.date,
                outageCnt = outages.size,
                downtimeSeconds = outages.sumOf { it.secondsEffectiveBetween(day.start, day.end, now) },
                incidents = outages.map { outage ->
                    StatusPageIncidentDto(
                        id = outage.id,
                        startedAt = outage.startedAt,
                        endedAt = outage.endedAt,
                        status = if (outage.endedAt == null) IncidentStatus.ONGOING else IncidentStatus.RESOLVED,
                        notes = publicNotesByEvent[outage.id].orEmpty(),
                    )
                },
            )
        }
    }

    private data class StatusHistoryDay(val date: LocalDate, val start: OffsetDateTime, val end: OffsetDateTime)

    private data class OverallStats(
        val monitors: List<MonitorSummary>,
        val uptimeAggregate: UptimeAggregate,
        val monitorsInMaintenance: Set<NumericMonitorID>,
        val statesOutsideMaintenance: MonitorStateCounts,
        val statesInMaintenance: MonitorStateCounts,
        val uptimeStats: ActualUptimeStats,
        val historicalUptimeStats: HistoricalUptimeStatsDto,
    )

    private data class DashboardFigures(
        val overallStats: OverallStats,
        val timeline: List<UptimeTimelineSlot>,
        val leastReliableMonitors: List<UnreliableMonitor>,
    )
}

/**
 * The uptime related figures of a single monitor over a period, calculated together from the same events.
 */
data class UptimeOverview(
    val uptimeRatio: Double?,
    val statusHistory: List<StatusHistoryDto>,
)

/**
 * The uptime events of a period added up on the database side. Every event is clipped to the period, and measured
 * between the whole seconds of its ends, see [getDurationOfEvent]. An incident is a DOWN event, the time to resolve it
 * is measured from its very start, even if it started before the period.
 */
data class UptimeAggregate(
    val uptimeSeconds: Long,
    val downtimeSeconds: Long,
    val incidents: Int,
    val affectedMonitors: Int,
    val ongoingIncidents: Int,
    val ongoingIncidentsInMaintenance: Int,
    val resolvedIncidents: Int,
    val resolvedIncidentSeconds: Long,
) {
    fun toHistoricalUptimeStats(period: Duration) = HistoricalUptimeStatsDto(
        period = period.toString(),
        incidents = incidents,
        affectedMonitors = affectedMonitors,
        uptimeRatio = uptimeRatioOf(uptimeSeconds, downtimeSeconds),
        totalDowntimeSeconds = downtimeSeconds,
    )

    companion object {
        val EMPTY = UptimeAggregate(0, 0, 0, 0, 0, 0, 0, 0)
    }
}

/** What the events of a period contributed to a slot of the timeline, see [UptimeTimelineSlot]. */
data class UptimeTimelineAggregate(
    val uptimeSeconds: Long,
    val downtimeSeconds: Long,
    val incidents: Int,
)

/** The uptime events of a single monitor added up, the same way as the ones of [UptimeAggregate]. */
data class MonitorUptimeAggregate(
    val monitorId: Long,
    val uptimeSeconds: Long,
    val downtimeSeconds: Long,
    val incidents: Int,
) {
    fun toHistoricalUptimeStats(period: Duration) = HistoricalUptimeStatsDto(
        period = period.toString(),
        incidents = incidents,
        affectedMonitors = if (incidents > 0) 1 else 0,
        uptimeRatio = uptimeRatioOf(uptimeSeconds, downtimeSeconds),
        totalDowntimeSeconds = downtimeSeconds,
    )
}

/**
 * The instant the figures are anchored to. The database keeps its timestamps in microseconds, so the instant is cut to
 * them too, otherwise the figures calculated on the database side could differ at the edges of a whole second.
 */
private fun currentInstant(): OffsetDateTime = getCurrentTimestamp().truncatedTo(ChronoUnit.MICROS)

private fun uptimeRatioOf(uptimeSeconds: Long, downtimeSeconds: Long): Double? {
    val totalMeasuredSeconds = uptimeSeconds + downtimeSeconds
    return if (totalMeasuredSeconds > 0) uptimeSeconds.toDouble() / totalMeasuredSeconds else null
}

data class UptimeEventCalculationContext(
    val id: Long,
    val monitorId: Long,
    val isMonitorEnabled: Boolean,
    val status: UptimeStatus,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime,
) {
    fun effectiveEndDate(now: OffsetDateTime): OffsetDateTime =
        getEffectiveEndOfEvent(isMonitorEnabled, endedAt, updatedAt, now)

    /**
     * Whether the event was effective at any point in the `[from, to)` interval. An open event is only known to be
     * effective until its last update, and an event ending right at [from] still counts.
     */
    fun wasEffectiveBetween(from: OffsetDateTime, to: OffsetDateTime): Boolean =
        startedAt.isBefore(to) && !(endedAt ?: updatedAt).isBefore(from)

    /** How many seconds of the event fall into the `[from, to)` interval, see [effectiveEndDate]. */
    fun secondsEffectiveBetween(from: OffsetDateTime, to: OffsetDateTime, now: OffsetDateTime): Long {
        val start = maxOf(startedAt, from)
        val end = minOf(effectiveEndDate(now), to)
        return if (end.isAfter(start)) Duration.between(start, end).seconds else 0
    }
}

// The same rules as the ones resolving the windows of the monitors on the database side
private fun MaintenanceWindowDetailsDto.affects(monitor: MonitorSummary): Boolean =
    global || monitor.monitorId in monitors || monitor.category?.let { it in categories } == true
