package com.kuvaszuptime.kuvasz.util

import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.toOption
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.Emoji
import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.StructuredDownMessage
import com.kuvaszuptime.kuvasz.models.StructuredUpMessage
import com.kuvaszuptime.kuvasz.models.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun UptimeMonitorEvent.toUptimeStatus(): UptimeStatus =
    when (this) {
        is MonitorUpEvent -> UptimeStatus.UP
        is MonitorDownEvent -> UptimeStatus.DOWN
    }

fun UptimeMonitorEvent.toEmoji(): String =
    when (this) {
        is MonitorUpEvent -> Emoji.CHECK_OK
        is MonitorDownEvent -> Emoji.ALERT
    }

fun UptimeMonitorEvent.uptimeStatusEquals(previousEvent: UptimeEventPojo) =
    toUptimeStatus() == previousEvent.status

fun UptimeMonitorEvent.uptimeStatusNotEquals(previousEvent: UptimeEventPojo) =
    !uptimeStatusEquals(previousEvent)

fun UptimeMonitorEvent.getEndedEventDuration(): Option<Duration> =
    previousEvent.flatMap { previousEvent ->
        Option.fromNullable(
            if (uptimeStatusNotEquals(previousEvent)) {
                val diff = dispatchedAt.toEpochSecond() - previousEvent.startedAt.toEpochSecond()
                diff.toDuration(DurationUnit.SECONDS)
            } else null
        )
    }

fun UptimeMonitorEvent.runWhenStateChanges(toRun: (UptimeMonitorEvent) -> Unit) {
    return previousEvent.fold(
        { toRun(this) },
        { previousEvent ->
            if (uptimeStatusNotEquals(previousEvent)) {
                toRun(this)
            }
        }
    )
}

fun MonitorUpEvent.toPlainMessage(): String =
    toStructuredMessage().let { details ->
        listOfNotNull(
            details.summary,
            details.latency,
            details.previousDownTime.orNull()
        ).joinToString(". ")
    }

fun MonitorUpEvent.toStructuredMessage() =
    StructuredUpMessage(
        summary = "Your monitor \"${monitor.name}\" (${monitor.url}) is UP (${status.code})",
        latency = "Latency: ${latency}ms",
        previousDownTime = getEndedEventDuration().toDurationString().map { "Was down for $it" }
    )

fun MonitorDownEvent.toPlainMessage(): String =
    toStructuredMessage().let { details ->
        listOfNotNull(
            details.summary,
            details.error,
            details.previousUpTime.orNull()
        ).joinToString(". ")
    }

fun MonitorDownEvent.toStructuredMessage() =
    StructuredDownMessage(
        summary = "Your monitor \"${monitor.name}\" (${monitor.url}) is DOWN" +
                status.toOption().map { " (" + it.code + ")" }.getOrElse { "" },
        error = "Reason: ${error.message}",
        previousUpTime = getEndedEventDuration().toDurationString().map { "Was up for $it" }
    )
