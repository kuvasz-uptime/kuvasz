package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import jakarta.inject.Singleton
import java.time.Duration
import java.time.OffsetDateTime

@Singleton
class StatCalculator(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val uptimeEventRepository: HttpUptimeEventRepository,
) {
    @Suppress("NestedBlockDepth")
    fun calculateOverallHttpStats(period: Duration): HttpMonitoringStatsDto {
        val monitors = httpMonitorRepository.getMonitorsWithDetails()
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

        return HttpMonitoringStatsDto(
            actual = HttpMonitoringStatsDto.ActualMonitoringStats(
                uptimeStats = HttpMonitoringStatsDto.ActualMonitoringStats.ActualUptimeStats(
                    total = monitors.size,
                    down = downMonitors,
                    up = upMonitors,
                    paused = pausedMonitors,
                    inProgress = uptimeInProgressMonitors,
                    lastIncident = uptimeEventRepository.fetchLatestIncidentTimestamp(),
                ),
                sslStats = HttpMonitoringStatsDto.ActualMonitoringStats.SslStats(
                    invalid = sslInvalidMonitors,
                    valid = sslValidMonitors,
                    willExpire = sslWillExpireMonitors,
                    inProgress = sslInProgressMonitors,
                )
            ),
            history = HttpMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = calculateHistoricalHttpUptimeStats(period, uptimeEvents)
            )
        )
    }

    /**
     * Calculates historical uptime statistics for a specific monitor over a given period.
     */
    fun calculateHistoricalHttpUptimeStats(
        period: Duration,
        monitorId: Long,
    ): HistoricalUptimeStatsDto {
        val uptimeEvents = uptimeEventRepository.fetchAllInPeriod(period, monitorId)

        return calculateHistoricalHttpUptimeStats(period, uptimeEvents)
    }

    /**
     * Calculates historical uptime statistics based on a list of uptime events and a period's start time.
     */
    fun calculateHistoricalHttpUptimeStats(
        period: Duration,
        uptimeEvents: List<UptimeEventCalculationContext>,
    ): HistoricalUptimeStatsDto {
        val periodStart = getCurrentTimestamp().minus(period)
        val monitorsWithIncidents: MutableSet<Long> = mutableSetOf()
        var historicalIncidentCnt = 0
        var historicalUptimeSeconds = 0L
        var historicalDowntimeSeconds = 0L

        uptimeEvents.forEach { uptimeEvent ->
            val effectiveStartDate = maxOf(uptimeEvent.startedAt, periodStart)
            val duration = getDurationOfEvent(
                isMonitorEnabled = uptimeEvent.isMonitorEnabled,
                startedAt = effectiveStartDate,
                endedAt = uptimeEvent.endedAt,
                updatedAt = uptimeEvent.updatedAt,
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
}

data class UptimeEventCalculationContext(
    val monitorId: Long,
    val isMonitorEnabled: Boolean,
    val status: UptimeStatus,
    val startedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
    val updatedAt: OffsetDateTime,
)
