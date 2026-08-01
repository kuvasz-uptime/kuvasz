package com.kuvaszuptime.kuvasz.models.dto.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import java.time.OffsetDateTime

sealed interface MonitorDetailsDto {
    val id: Long
    val name: String
    val enabled: Boolean
    val uptimeStatus: UptimeStatus?
    val uptimeStatusStartedAt: OffsetDateTime?
    val lastUptimeCheck: OffsetDateTime?
    val uptimeError: String?
    val inMaintenance: Boolean
    val statusPages: Set<String>
}

fun MonitorDetailsDto.monitorType(): MonitorType = when (this) {
    is HttpMonitorDetailsDto -> MonitorType.HTTP_SSL
    is PushMonitorDetailsDto -> MonitorType.PUSH
    is IcmpMonitorDetailsDto -> MonitorType.ICMP
    is TcpMonitorDetailsDto -> MonitorType.TCP
    is DnsMonitorDetailsDto -> MonitorType.DNS
}

fun MonitorDetailsDto.monitorId(): MonitorID = MonitorID(monitorType(), name)
