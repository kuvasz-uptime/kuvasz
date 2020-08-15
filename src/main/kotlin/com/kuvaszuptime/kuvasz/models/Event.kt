package com.kuvaszuptime.kuvasz.models

import arrow.core.Option
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.micronaut.http.HttpStatus
import java.net.URI
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

sealed class Event {
    val dispatchedAt = getCurrentTimestamp()
}

sealed class UptimeMonitorEvent : Event() {
    abstract val monitor: MonitorPojo
    abstract val previousEvent: Option<UptimeEventPojo>
}

data class MonitorUpEvent(
    override val monitor: MonitorPojo,
    val status: HttpStatus,
    val latency: Int,
    override val previousEvent: Option<UptimeEventPojo>
) : UptimeMonitorEvent()

data class MonitorDownEvent(
    override val monitor: MonitorPojo,
    val status: HttpStatus?,
    val error: Throwable,
    override val previousEvent: Option<UptimeEventPojo>
) : UptimeMonitorEvent()

data class RedirectEvent(
    val monitor: MonitorPojo,
    val redirectLocation: URI
) : Event()

fun UptimeMonitorEvent.toUptimeStatus(): UptimeStatus =
    when (this) {
        is MonitorUpEvent -> UptimeStatus.UP
        is MonitorDownEvent -> UptimeStatus.DOWN
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

fun UptimeMonitorEvent.toMessage(): String =
    when (this) {
        is MonitorUpEvent -> toMessage()
        is MonitorDownEvent -> toMessage()
    }

fun MonitorUpEvent.toMessage(): String {
    val message = "Your monitor \"${monitor.name}\" (${monitor.url}) is UP (${status.code}). Latency was: ${latency}ms."
    return getEndedEventDuration().toDurationString().fold(
        { message },
        { "$message Was down for $it." }
    )
}

fun MonitorDownEvent.toMessage(): String {
    val message = "Your monitor \"${monitor.name}\" (${monitor.url}) is DOWN. Reason: ${error.message}."
    return getEndedEventDuration().toDurationString().fold(
        { message },
        { "$message Was up for $it." }
    )
}
