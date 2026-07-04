package com.kuvaszuptime.kuvasz.models.dto.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID

sealed interface MonitorDetailsDto {
    val id: Long
    val name: String
    val enabled: Boolean
    val uptimeStatus: UptimeStatus?
    val uptimeError: String?
    val inMaintenance: Boolean
}

fun MonitorDetailsDto.monitorId(): MonitorID = when (this) {
    is HttpMonitorDetailsDto -> MonitorID(MonitorType.HTTP_SSL, this.name)
    is PushMonitorDetailsDto -> MonitorID(MonitorType.PUSH, this.name)
    is IcmpMonitorDetailsDto -> MonitorID(MonitorType.ICMP, this.name)
}
