package com.kuvaszuptime.kuvasz.models.dto.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.WithDisplayName
import java.time.OffsetDateTime

@Suppress("ComplexInterface")
sealed interface MonitorDetailsDto : WithDisplayName {
    val id: Long
    override val name: String
    override val displayName: String?
    val enabled: Boolean
    val uptimeStatus: UptimeStatus?
    val uptimeStatusStartedAt: OffsetDateTime?
    val lastUptimeCheck: OffsetDateTime?
    val uptimeError: String?
    val inMaintenance: Boolean
    val statusPages: Set<String>
    val category: String?
    val ignoreConnectivityCheck: Boolean
}

fun MonitorDetailsDto.monitorType(): MonitorType = when (this) {
    is HttpMonitorDetailsDto -> MonitorType.HTTP_SSL
    is PushMonitorDetailsDto -> MonitorType.PUSH
    is IcmpMonitorDetailsDto -> MonitorType.ICMP
    is TcpMonitorDetailsDto -> MonitorType.TCP
    is DnsMonitorDetailsDto -> MonitorType.DNS
    is DockerMonitorDetailsDto -> MonitorType.DOCKER
}

fun MonitorDetailsDto.monitorId(): MonitorID = MonitorID(monitorType(), name)

fun Iterable<MonitorDetailsDto>.monitorsWithCategory(): Map<MonitorID, String?> =
    associate { it.monitorId() to it.category }
