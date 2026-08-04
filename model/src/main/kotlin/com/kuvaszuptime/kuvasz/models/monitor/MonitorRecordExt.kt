package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.dns.monitorId as dnsMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId as httpMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.icmp.monitorId as icmpMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId as pushMonitorId
import com.kuvaszuptime.kuvasz.models.monitor.tcp.monitorId as tcpMonitorId

fun MonitorRecord.monitorId(): MonitorID = when (this) {
    is HttpMonitorRecord -> httpMonitorId()
    is PushMonitorRecord -> pushMonitorId()
    is IcmpMonitorRecord -> icmpMonitorId()
    is TcpMonitorRecord -> tcpMonitorId()
    is DnsMonitorRecord -> dnsMonitorId()
    else -> error("Unknown monitor type: ${this::class}")
}

val MonitorRecord.relativeDetailsUrl: String
    get() = when (this) {
        is HttpMonitorRecord -> "/http-monitors/${this.id}"
        is PushMonitorRecord -> "/push-monitors/${this.id}"
        is IcmpMonitorRecord -> "/icmp-monitors/${this.id}"
        is TcpMonitorRecord -> "/tcp-monitors/${this.id}"
        is DnsMonitorRecord -> "/dns-monitors/${this.id}"
        else -> error("Unknown monitor type: ${this::class}")
    }
