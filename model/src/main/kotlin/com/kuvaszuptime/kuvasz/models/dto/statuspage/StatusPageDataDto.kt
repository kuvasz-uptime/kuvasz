package com.kuvaszuptime.kuvasz.models.dto.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import java.time.LocalDate

// TODO WIP
data class StatusPageDataDto(
    val title: String,
    val period: String,
    val systemStatus: SystemStatus,
    val monitors: List<StatusPageMonitorDetailsDto>,
)

data class StatusPageMonitorDetailsDto(
    val name: String,
    val averageLatencyInMs: Int?,
    val uptimeRatio: Double,
    val uptimeStatus: UptimeStatus,
    val uptimeStatusHistory: List<StatusHistoryDto>,
) {
    data class StatusHistoryDto(
        val date: LocalDate,
        val outageCnt: Int,
    )
}
