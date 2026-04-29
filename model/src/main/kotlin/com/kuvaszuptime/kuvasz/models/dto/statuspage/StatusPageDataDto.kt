package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import io.swagger.v3.oas.annotations.media.Schema
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

@Schema(
    oneOf = [
        StatusPagePushMonitorDetailsDto::class,
        StatusPageHttpMonitorDetailsDto::class,
    ]
)
// JSON subtypes are needed only for the tests
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = false,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = StatusPagePushMonitorDetailsDto::class, name = "push"),
    JsonSubTypes.Type(value = StatusPageHttpMonitorDetailsDto::class, name = "http"),
)
sealed interface StatusPageMonitorDetailsDto {
    val name: String
    val type: String
    val lastCheck: OffsetDateTime?
    val uptimeRatio: Double?
    val uptimeStatus: UptimeStatus?
    val uptimeStatusHistory: List<StatusHistoryDto>
}

data class StatusPagePushMonitorDetailsDto(
    override val name: String,
    override val type: String = "push",
    override val lastCheck: OffsetDateTime?,
    override val uptimeRatio: Double?,
    override val uptimeStatus: UptimeStatus?,
    override val uptimeStatusHistory: List<StatusHistoryDto>,
    val lastHeartbeat: OffsetDateTime?,
) : StatusPageMonitorDetailsDto

data class StatusPageHttpMonitorDetailsDto(
    override val name: String,
    override val type: String = "http",
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
