package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import java.time.LocalDate
import java.time.OffsetDateTime

data class StatusPageDataDto(
    val title: String,
    val customLogoUrl: String?,
    val customFaviconUrl: String?,
    val systemStatus: SystemStatus,
    val generatedAt: OffsetDateTime,
    val monitors: List<StatusPageMonitorDetailsDto>,
)

sealed interface StatusPageMonitorDetailsDto {
    val name: String
    val lastCheck: OffsetDateTime?
    val uptimeRatio: Double?
    val uptimeStatus: UptimeStatus?
    val uptimeStatusHistory: List<StatusHistoryDto>
}

data class StatusPagePushMonitorDetailsDto(
    override val name: String,
    override val lastCheck: OffsetDateTime?,
    override val uptimeRatio: Double?,
    override val uptimeStatus: UptimeStatus?,
    override val uptimeStatusHistory: List<StatusHistoryDto>,
    val lastHeartbeat: OffsetDateTime?,
) : StatusPageMonitorDetailsDto

data class StatusPageHttpMonitorDetailsDto(
    override val name: String,
    override val lastCheck: OffsetDateTime?,
    override val uptimeRatio: Double?,
    override val uptimeStatus: UptimeStatus?,
    override val uptimeStatusHistory: List<StatusHistoryDto>,
    val averageLatencyInMs: Int?,
) : StatusPageMonitorDetailsDto

/**
 * A data point in the uptime status history of the monitor.
 *
 * @param date The date of the data point.
 * @param outageCnt The number of outages that occurred on that date. Null if there is no information for the given
 * date (e.g. monitor was created after that date).
 */
data class StatusHistoryDto(
    val date: LocalDate,
    val outageCnt: Int?,
)
