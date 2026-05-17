package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.toDurationString

sealed class IcmpUptimeMonitorEvent : UptimeMonitorEvent() {
    abstract override val previousEvent: IcmpUptimeEventRecord?
}

data class IcmpMonitorUpEvent(
    override val monitor: IcmpMonitorRecord,
    override val previousEvent: IcmpUptimeEventRecord?,
    val latencyInMs: Int?,
    val packetLossPercentage: Int,
) : IcmpUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.UP

    override fun toStructuredMessage() = StructuredIcmpMonitorUpMessage(
        summary = Messages.yourIcmpMonitorIsUp(monitor.name),
        latency = latencyInMs?.let { Messages.icmpLatency(it.toString()) },
        packetLoss = Messages.icmpPacketLoss(packetLossPercentage.toString()),
        previousDownTime = getEndedEventDuration().toDurationString()?.let { Messages.wasDownFor(it) },
    )
}

data class IcmpMonitorDownEvent(
    override val monitor: IcmpMonitorRecord,
    val error: String,
    override val previousEvent: IcmpUptimeEventRecord?,
    val packetLossPercentage: Int,
) : IcmpUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.DOWN

    override fun toStructuredMessage() = StructuredIcmpMonitorDownMessage(
        summary = Messages.yourIcmpMonitorIsDown(monitor.name),
        error = Messages.reasonExplanation(error),
        packetLoss = Messages.icmpPacketLoss(packetLossPercentage.toString()),
        previousUpTime = getEndedEventDuration().toDurationString()?.let { Messages.wasUpFor(it) },
    )
}
