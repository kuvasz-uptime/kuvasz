package com.kuvaszuptime.kuvasz.models.dashboard

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.ActualUptimeStats
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import java.time.Duration
import java.time.OffsetDateTime

/**
 * Everything the dashboard shows, calculated across every monitor type over the given [period].
 */
data class DashboardOverview(
    val period: Duration,
    val uptimeStats: DashboardUptimeStats,
    // The ongoing ones first, then the latest resolved ones, as many as the dashboard shows
    val recentIncidents: List<IncidentDto>,
    // The enabled windows that are either active or start within the lookahead
    val maintenanceWindows: List<MaintenanceWindowDetailsDto>,
    val maintenanceLookahead: Duration,
)

/**
 * The uptime figures of every monitor type merged together, and the per-type breakdown of the types in use.
 */
data class DashboardUptimeStats(
    val actual: ActualUptimeStats,
    val history: HistoricalUptimeStatsDto,
    val incidents: DashboardIncidentStats,
    // The monitors that are down, but under an active maintenance window
    val downInMaintenance: Int,
    val sslStats: HttpMonitoringStatsDto.ActualMonitoringStats.SslStats,
    val timeline: List<UptimeTimelineSlot>,
    val byType: List<MonitorTypeUptimeStats>,
    // The invalid certificates first, then the ones about to expire, the soonest expiring first
    val certificatesWithIssues: List<HttpMonitorDetailsDto>,
    val monitorsInMaintenance: Set<NumericMonitorID>,
    // The ones with the most downtime first, then the ones with the most incidents
    val leastReliableMonitors: List<UnreliableMonitor>,
)

/**
 * A monitor that went down at least once during the period, with its current state and its figures of the period.
 */
data class UnreliableMonitor(
    val id: NumericMonitorID,
    val name: String,
    val enabled: Boolean,
    val uptimeStatus: UptimeStatus?,
    val inMaintenance: Boolean,
    val history: HistoricalUptimeStatsDto,
)

/**
 * The incidents (DOWN events) that were open at any point during the period, the same ones as the history counts.
 */
data class DashboardIncidentStats(
    val ongoing: Int,
    val resolved: Int,
    val meanTimeToResolveSeconds: Long?,
)

data class MonitorTypeUptimeStats(
    val type: MonitorType,
    val actual: ActualUptimeStats,
    val history: HistoricalUptimeStatsDto,
    val timeline: List<UptimeTimelineSlot>,
)

/**
 * A consecutive slice of the dashboard's period, summarizing the uptime events of every monitor that overlapped it.
 */
data class UptimeTimelineSlot(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
    val uptimeSeconds: Long,
    val downtimeSeconds: Long,
    // The incidents that started in the slot (or before the period, in the first one), even if they lasted longer
    val incidents: Int,
) {
    val uptimeRatio: Double?
        get() = (uptimeSeconds + downtimeSeconds)
            .takeIf { it > 0 }
            ?.let { uptimeSeconds.toDouble() / it }
}
