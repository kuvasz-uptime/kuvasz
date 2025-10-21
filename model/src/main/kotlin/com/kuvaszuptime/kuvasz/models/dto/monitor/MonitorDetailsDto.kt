package com.kuvaszuptime.kuvasz.models.dto.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus

interface MonitorDetailsDto {
    val id: Long
    val name: String
    val enabled: Boolean
    val uptimeStatus: UptimeStatus?
    val uptimeError: String?
}
