package com.kuvaszuptime.kuvasz.models.dto.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import java.time.OffsetDateTime

/**
 * The few properties of a monitor that its statistics rely on, which are way cheaper to fetch than the whole
 * [MonitorDetailsDto], e.g. every time the dashboard is refreshed.
 */
sealed class MonitorSummary {
    abstract val type: MonitorType
    abstract val id: Long
    abstract val name: String
    abstract val enabled: Boolean
    abstract val category: String?
    abstract val uptimeStatus: UptimeStatus?

    // A getter, as the properties of the subclasses aren't initialized yet when the superclass is
    val monitorId: MonitorID
        get() = MonitorID(type, name)
}

data class HttpMonitorSummary(
    override val id: Long,
    override val name: String,
    override val enabled: Boolean,
    override val category: String?,
    override val uptimeStatus: UptimeStatus?,
    val sslCheckEnabled: Boolean,
    val sslStatus: SslStatus?,
    val sslValidUntil: OffsetDateTime?,
    val sslError: String?,
) : MonitorSummary() {
    override val type: MonitorType = MonitorType.HTTP_SSL
}

data class PushMonitorSummary(
    override val id: Long,
    override val name: String,
    override val enabled: Boolean,
    override val category: String?,
    override val uptimeStatus: UptimeStatus?,
) : MonitorSummary() {
    override val type: MonitorType = MonitorType.PUSH
}

data class IcmpMonitorSummary(
    override val id: Long,
    override val name: String,
    override val enabled: Boolean,
    override val category: String?,
    override val uptimeStatus: UptimeStatus?,
) : MonitorSummary() {
    override val type: MonitorType = MonitorType.ICMP
}

data class TcpMonitorSummary(
    override val id: Long,
    override val name: String,
    override val enabled: Boolean,
    override val category: String?,
    override val uptimeStatus: UptimeStatus?,
) : MonitorSummary() {
    override val type: MonitorType = MonitorType.TCP
}

data class DnsMonitorSummary(
    override val id: Long,
    override val name: String,
    override val enabled: Boolean,
    override val category: String?,
    override val uptimeStatus: UptimeStatus?,
) : MonitorSummary() {
    override val type: MonitorType = MonitorType.DNS
}

data class DockerMonitorSummary(
    override val id: Long,
    override val name: String,
    override val enabled: Boolean,
    override val category: String?,
    override val uptimeStatus: UptimeStatus?,
) : MonitorSummary() {
    override val type: MonitorType = MonitorType.DOCKER
}
