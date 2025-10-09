package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.util.toDurationString

sealed class PushUptimeMonitorEvent : UptimeMonitorEvent() {
    abstract override val previousEvent: PushUptimeEventRecord?
}

data class PushMonitorUpEvent(
    override val monitor: PushMonitorRecord,
    override val previousEvent: PushUptimeEventRecord?
) : PushUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.UP

    override fun toStructuredMessage() =
        StructuredPushMonitorUpMessage(
            summary = Messages.yourPushMonitorIsUp(monitor.name),
            previousDownTime = getEndedEventDuration().toDurationString()?.let { Messages.wasDownFor(it) }
        )
}

data class PushMonitorDownEvent(
    override val monitor: PushMonitorRecord,
    val error: String,
    override val previousEvent: PushUptimeEventRecord?,
    val isManual: Boolean = false,
) : PushUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.DOWN

    override fun toStructuredMessage(): StructuredMonitorDownMessage =
        StructuredMonitorDownMessage(
            summary = Messages.yourPushMonitorIsDown(monitor.name),
            error = Messages.reasonExplanation(error),
            previousUpTime = getEndedEventDuration().toDurationString()?.let { Messages.wasUpFor(it) }
        )
}
