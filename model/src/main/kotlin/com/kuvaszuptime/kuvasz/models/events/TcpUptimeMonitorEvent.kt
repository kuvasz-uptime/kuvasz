package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.toDurationString

sealed class TcpUptimeMonitorEvent : UptimeMonitorEvent() {
    abstract override val previousEvent: TcpUptimeEventRecord?
}

data class TcpMonitorUpEvent(
    override val monitor: TcpMonitorRecord,
    override val previousEvent: TcpUptimeEventRecord?,
    val latencyInMs: Int?,
) : TcpUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.UP

    override fun toStructuredMessage() = StructuredTcpMonitorUpMessage(
        summary = Messages.yourTcpMonitorIsUp(monitor.name),
        latency = latencyInMs?.let { Messages.tcpLatency(it.toString()) },
        previousDownTime = getEndedEventDuration().toDurationString()?.let { Messages.wasDownFor(it) },
    )
}

data class TcpMonitorDownEvent(
    override val monitor: TcpMonitorRecord,
    val error: String,
    override val previousEvent: TcpUptimeEventRecord?,
    val latencyInMs: Int? = null,
) : TcpUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.DOWN

    override fun toStructuredMessage() = StructuredTcpMonitorDownMessage(
        summary = Messages.yourTcpMonitorIsDown(monitor.name),
        error = Messages.reasonExplanation(error),
        previousUpTime = getEndedEventDuration().toDurationString()?.let { Messages.wasUpFor(it) },
    )
}
