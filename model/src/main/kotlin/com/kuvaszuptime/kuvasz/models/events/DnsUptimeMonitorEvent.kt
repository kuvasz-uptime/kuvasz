package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.toDurationString

sealed class DnsUptimeMonitorEvent : UptimeMonitorEvent() {
    abstract override val previousEvent: DnsUptimeEventRecord?
}

data class DnsMonitorUpEvent(
    override val monitor: DnsMonitorRecord,
    override val previousEvent: DnsUptimeEventRecord?,
    val latencyInMs: Int?,
) : DnsUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.UP

    override fun toStructuredMessage() = StructuredDnsMonitorUpMessage(
        summary = Messages.yourDnsMonitorIsUp(monitor.name),
        latency = latencyInMs?.let { Messages.dnsLatency(it.toString()) },
        previousDownTime = getEndedEventDuration().toDurationString()?.let { Messages.wasDownFor(it) },
    )
}

data class DnsMonitorDownEvent(
    override val monitor: DnsMonitorRecord,
    val error: String,
    override val previousEvent: DnsUptimeEventRecord?,
    val latencyInMs: Int? = null,
) : DnsUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.DOWN

    override fun toStructuredMessage() = StructuredDnsMonitorDownMessage(
        summary = Messages.yourDnsMonitorIsDown(monitor.name),
        error = Messages.reasonExplanation(error),
        previousUpTime = getEndedEventDuration().toDurationString()?.let { Messages.wasUpFor(it) },
    )
}
