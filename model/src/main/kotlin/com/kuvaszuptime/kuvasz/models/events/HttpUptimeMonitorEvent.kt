package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.monitor.http.safeDisplayUrl
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.micronaut.http.HttpStatus

sealed class HttpUptimeMonitorEvent : UptimeMonitorEvent() {
    abstract override val previousEvent: HttpUptimeEventRecord?
}

data class HttpMonitorUpEvent(
    override val monitor: HttpMonitorRecord,
    val status: HttpStatus,
    val latency: Int,
    override val previousEvent: HttpUptimeEventRecord?
) : HttpUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.UP

    override fun toStructuredMessage() =
        StructuredHttpMonitorUpMessage(
            summary = Messages.yourMonitorIsUp(monitor.name, monitor.safeDisplayUrl, status.code),
            latency = Messages.latencyIs(latency),
            previousDownTime = getEndedEventDuration().toDurationString()?.let { Messages.wasDownFor(it) }
        )
}

data class HttpMonitorDownEvent(
    override val monitor: HttpMonitorRecord,
    val status: HttpStatus?,
    val error: Exception,
    override val previousEvent: HttpUptimeEventRecord?
) : HttpUptimeMonitorEvent() {

    override val uptimeStatus = UptimeStatus.DOWN

    override fun toStructuredMessage(): StructuredMonitorDownMessage {
        val sanitizedError = error.message?.sanitizeAsError()
        val structuredError = if (status != null) {
            "${status.code} ${status.reason}".let { statusFragment ->
                if (sanitizedError != null) {
                    "$statusFragment: $sanitizedError"
                } else {
                    statusFragment
                }
            }
        } else {
            sanitizedError
        }

        return StructuredMonitorDownMessage(
            summary = Messages.yourMonitorIsDown(
                monitor.name,
                monitor.safeDisplayUrl,
                status?.let { " (" + it.code + ")" }.orEmpty(),
            ),
            error = Messages.reasonExplanation(structuredError.orEmpty()),
            previousUpTime = getEndedEventDuration().toDurationString()?.let { Messages.wasUpFor(it) }
        )
    }
}
