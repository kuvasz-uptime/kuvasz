package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.getDurationOfEvent
import jakarta.inject.Singleton
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

@Singleton
class StatCalculator(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val icmpUptimeEventRepository: IcmpUptimeEventRepository,
) {
    @Suppress("NestedBlockDepth")
    fun calculateOverallHttpStats(period: Duration): HttpMonitoringStatsDto {
        val monitors = httpMonitorRepository.getMonitorsWithDetails()
        val uptimeEvents = httpUptimeEventRepository.fetchAllInPeriod(period)
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
                    lastIncident = httpUptimeEventRepository.fetchLatestIncidentTimestamp(),
                ),
                sslStats = HttpMonitoringStatsDto.ActualMonitoringStats.SslStats(
                    invalid = sslInvalidMonitors,
                    valid = sslValidMonitors,
                    willExpire = sslWillExpireMonitors,
                    inProgress = sslInProgressMonitors,
                )
            ),
            history = HttpMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = calculateHistoricalUptimeStats(period, uptimeEvents)
            )
        )
    }

    fun calculateOverallPushStats(period: Duration): PushMonitoringStatsDto {
        val monitors = pushMonitorRepository.getMonitorsWithDetails()
        val uptimeEvents = pushUptimeEventRepository.fetchAllInPeriod(period)
        var downMonitors = 0
        var upMonitors = 0
        var pausedMonitors = 0
        var uptimeInProgressMonitors = 0

        monitors.forEach { monitor ->
            if (monitor.enabled) {
                // Uptime calculations
                when (monitor.uptimeStatus) {
                    UptimeStatus.DOWN -> downMonitors++
                    UptimeStatus.UP -> upMonitors++
                    null -> uptimeInProgressMonitors++
                }
            } else {
                pausedMonitors++
            }
        }

        return PushMonitoringStatsDto(
            actual = PushMonitoringStatsDto.ActualMonitoringStats(
                uptimeStats = PushMonitoringStatsDto.ActualMonitoringStats.ActualUptimeStats(
                    total = monitors.size,
                    down = downMonitors,
                    up = upMonitors,
                    paused = pausedMonitors,
                    inProgress = uptimeInProgressMonitors,
                    lastIncident = pushUptimeEventRepository.fetchLatestIncidentTimestamp(),
                ),
            ),
            history = PushMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = calculateHistoricalUptimeStats(period, uptimeEvents)
            )
        )
    }

    /**
     * Calculates historical uptime statistics for a specific HTTP monitor over a given period.
     */
    fun calculateHistoricalHttpUptimeStats(
        period: Duration,
        monitorId: Long,
    ): HistoricalUptimeStatsDto {
        val uptimeEvents = httpUptimeEventRepository.fetchAllInPeriod(period, monitorId)

        return calculateHistoricalUptimeStats(period, uptimeEvents)
    }

    /**
     * Calculates historical uptime statistics for a specific HTTP monitor over a given period.
     */
    fun calculateHistoricalPushUptimeStats(
        period: Duration,
        monitorId: Long,
    ): HistoricalUptimeStatsDto {
        val uptimeEvents = pushUptimeEventRepository.fetchAllInPeriod(period, monitorId)

        return calculateHistoricalUptimeStats(period, uptimeEvents)
    }

    fun calculateOverallIcmpStats(period: Duration): IcmpMonitoringStatsDto {
        val monitors = icmpMonitorRepository.getMonitorsWithDetails()
        val uptimeEvents = icmpUptimeEventRepository.fetchAllInPeriod(period)
        var downMonitors = 0
        var upMonitors = 0
        var pausedMonitors = 0
        var uptimeInProgressMonitors = 0

        monitors.forEach { monitor ->
            if (monitor.enabled) {
                when (monitor.uptimeStatus) {
                    UptimeStatus.DOWN -> downMonitors++
                    UptimeStatus.UP -> upMonitors++
                    null -> uptimeInProgressMonitors++
                }
            } else {
                pausedMonitors++
            }
        }

        return IcmpMonitoringStatsDto(
            actual = IcmpMonitoringStatsDto.ActualMonitoringStats(
                uptimeStats = IcmpMonitoringStatsDto.ActualMonitoringStats.ActualUptimeStats(
                    total = monitors.size,
                    down = downMonitors,
                    up = upMonitors,
                    paused = pausedMonitors,
                    inProgress = uptimeInProgressMonitors,
                    lastIncident = icmpUptimeEventRepository.fetchLatestIncidentTimestamp(),
                ),
            ),
            history = IcmpMonitoringStatsDto.HistoricalMonitoringStats(
                uptimeStats = calculateHistoricalUptimeStats(period, uptimeEvents)
            )
        )
    }

    fun calculateHistoricalIcmpUptimeStats(
        period: Duration,
        monitorId: Long,
    ): HistoricalUptimeStatsDto {
        val uptimeEvents = icmpUptimeEventRepository.fetchAllInPeriod(period, monitorId)

        return calculateHistoricalUptimeStats(period, uptimeEvents)
    }

    /**
     * Calculates historical uptime statistics based on a list of uptime events and a period's start time.
     */
    private fun calculateHistoricalUptimeStats(
        period: Duration,
        uptimeEvents: List<UptimeEventCalculationContext>,
    ): HistoricalUptimeStatsDto {
        val periodStart = getCurrentTimestamp().minus(period)
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
     * Generates a list of daily uptime status history over a specified period based on uptime events, by summarizing
     * the number of outages (DOWN events) for each day. It returns an entry for each day in the period, even if there
     * were no events on that day.
     *
     * @param period The duration over which to generate the history.
     * @param uptimeEvents A list of uptime events to analyze.
     *
     * @return A list of [StatusHistoryDto] representing the daily uptime status history.
     */
    fun generateUptimeHistoryOverview(
        period: Duration,
        uptimeEvents: List<UptimeEventCalculationContext>,
    ): List<StatusHistoryDto> {
        val periodStartTimestamp: OffsetDateTime = getCurrentTimestamp().minus(period)
        val periodEnd: LocalDate = getCurrentTimestamp().toLocalDate()
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
}

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
