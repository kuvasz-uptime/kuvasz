package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitoringStatsDto
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.util.diffToDuration
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import jakarta.inject.Singleton
import java.time.Duration

@Singleton
class StatCalculator(
    private val monitorCrudService: MonitorCrudService,
    private val uptimeEventRepository: UptimeEventRepository,
) {
    @Suppress("NestedBlockDepth")
    fun calculateOverallStats(period: Duration): MonitoringStatsDto {
        val monitors = monitorCrudService.getMonitorsWithDetails()
        val uptimeEvents = uptimeEventRepository.fetchAllInPeriod(period)
        var downMonitors = 0
        var upMonitors = 0
        var pausedMonitors = 0
        var uptimeInProgressMonitors = 0
        var sslValidMonitors = 0
        var sslInvalidMonitors = 0
        var sslWillExpireMonitors = 0
        var sslInProgressMonitors = 0

        monitors.forEach { monitor ->
            if (monitor.enabled) {
                // Uptime calculations
                when (monitor.uptimeStatus) {
                    UptimeStatus.DOWN -> downMonitors++
                    UptimeStatus.UP -> upMonitors++
                    null -> uptimeInProgressMonitors++
                }

                // SSL calculations
                if (monitor.sslCheckEnabled) {
                    when (monitor.sslStatus) {
                        SslStatus.VALID -> sslValidMonitors++
                        SslStatus.INVALID -> sslInvalidMonitors++
                        SslStatus.WILL_EXPIRE -> sslWillExpireMonitors++
                        null -> sslInProgressMonitors++
                    }
                }
            } else {
                pausedMonitors++
            }
        }

        return MonitoringStatsDto(
            actual = MonitoringStatsDto.ActualMonitoringStats(
                uptimeStats = MonitoringStatsDto.ActualMonitoringStats.ActualUptimeStats(
                    total = monitors.size,
                    down = downMonitors,
                    up = upMonitors,
                    paused = pausedMonitors,
                    inProgress = uptimeInProgressMonitors,
                    lastIncident = uptimeEventRepository.fetchLatestIncidentTimestamp(),
                ),
                sslStats = MonitoringStatsDto.ActualMonitoringStats.SslStats(
                    invalid = sslInvalidMonitors,
                    valid = sslValidMonitors,
                    willExpire = sslWillExpireMonitors,
                    inProgress = sslInProgressMonitors,
                )
            ),
            history = MonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = calculateHistoricalStats(uptimeEvents)
            )
        )
    }

    fun calculateHistoricalStats(
        uptimeEvents: List<UptimeEventRecord>,
    ): MonitoringStatsDto.HistoricalMonitoringStats.HistoricalUptimeStats {
        val monitorsWithIncidents: MutableSet<Long> = mutableSetOf()
        var historicalIncidentCnt = 0
        var historicalUptimeSeconds = 0L
        var historicalDowntimeSeconds = 0L

        uptimeEvents.forEach { uptimeEvent ->
            val duration = uptimeEvent.startedAt
                .diffToDuration(uptimeEvent.endedAt ?: getCurrentTimestamp()).inWholeSeconds

            if (uptimeEvent.status == UptimeStatus.DOWN) {
                monitorsWithIncidents.add(uptimeEvent.monitorId)
                historicalIncidentCnt++
                historicalDowntimeSeconds += duration
            } else if (uptimeEvent.status == UptimeStatus.UP) {
                historicalUptimeSeconds += duration
            }
        }

        return MonitoringStatsDto.HistoricalMonitoringStats.HistoricalUptimeStats(
            incidents = historicalIncidentCnt,
            affectedMonitors = monitorsWithIncidents.size,
            uptimeRatio = if (historicalUptimeSeconds + historicalDowntimeSeconds > 0) {
                historicalUptimeSeconds.toDouble() / (historicalUptimeSeconds + historicalDowntimeSeconds)
            } else {
                null
            }
        )
    }
}
