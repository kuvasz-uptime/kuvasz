package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardIncidentStats
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardOverview
import com.kuvaszuptime.kuvasz.models.dashboard.DashboardUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorStateCounts
import com.kuvaszuptime.kuvasz.models.dashboard.MonitorTypeUptimeStats
import com.kuvaszuptime.kuvasz.models.dashboard.UnreliableMonitor
import com.kuvaszuptime.kuvasz.models.dashboard.UptimeTimelineSlot
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorSummary
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto.ActualMonitoringStats.SslStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import java.time.Duration
import java.time.OffsetDateTime

fun dashboardActualStats(
    total: Int = 0,
    up: Int = 0,
    down: Int = 0,
    paused: Int = 0,
    inProgress: Int = 0,
    inMaintenance: Int = 0,
    lastIncident: OffsetDateTime? = null,
) = ActualUptimeStats(
    total = total,
    down = down,
    up = up,
    paused = paused,
    inProgress = inProgress,
    inMaintenance = inMaintenance,
    lastIncident = lastIncident,
)

/**
 * A dashboard overview of the given figures. Every monitor is a push monitor, so the overview has a single type row
 * (`push`) as long as there is any monitor.
 */
fun dashboardOverview(
    period: Duration,
    actual: ActualUptimeStats,
    inMaintenance: MonitorStateCounts = MonitorStateCounts(up = 0, down = 0, pending = 0),
    leastReliableMonitors: List<UnreliableMonitor> = emptyList(),
    recentIncidents: List<IncidentDto> = emptyList(),
    incidents: DashboardIncidentStats = DashboardIncidentStats(0, 0, 0, null),
    sslStats: SslStats = SslStats(invalid = 0, valid = 0, willExpire = 0, inProgress = 0),
    typeTimeline: List<UptimeTimelineSlot> = emptyList(),
    moreOngoingIncidents: Int = 0,
    certificatesWithIssues: List<HttpMonitorSummary> = emptyList(),
    maintenanceWindows: List<MaintenanceWindowDetailsDto> = emptyList(),
    moreMaintenanceWindows: Int = 0,
    history: HistoricalUptimeStatsDto = HistoricalUptimeStatsDto(
        period = period.toString(),
        incidents = 0,
        affectedMonitors = 0,
        uptimeRatio = null,
        totalDowntimeSeconds = 0,
    ),
): DashboardOverview {
    val outsideMaintenance = MonitorStateCounts(
        up = actual.up - inMaintenance.up,
        down = actual.down - inMaintenance.down,
        pending = actual.inProgress - inMaintenance.pending,
    )
    return DashboardOverview(
        period = period,
        uptimeStats = DashboardUptimeStats(
            actual = actual,
            history = history,
            incidents = incidents,
            outsideMaintenance = outsideMaintenance,
            inMaintenance = inMaintenance,
            sslStats = sslStats,
            timeline = emptyList(),
            byType = if (actual.total > 0) {
                listOf(
                    MonitorTypeUptimeStats(
                        type = MonitorType.PUSH,
                        actual = actual,
                        outsideMaintenance = outsideMaintenance,
                        inMaintenance = inMaintenance,
                        history = HistoricalUptimeStatsDto(period.toString(), 0, 0, null, 0),
                        timeline = typeTimeline,
                    )
                )
            } else {
                emptyList()
            },
            certificatesWithIssues = certificatesWithIssues,
            monitorsInMaintenance = emptySet(),
            leastReliableMonitors = leastReliableMonitors,
        ),
        recentIncidents = recentIncidents,
        moreOngoingIncidents = moreOngoingIncidents,
        maintenanceWindows = maintenanceWindows,
        moreMaintenanceWindows = moreMaintenanceWindows,
    )
}
