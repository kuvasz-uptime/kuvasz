package com.akobor.kuvasz.events

import arrow.core.Option
import com.akobor.kuvasz.enums.UptimeStatus
import com.akobor.kuvasz.tables.pojos.MonitorPojo
import com.akobor.kuvasz.tables.pojos.UptimeEventPojo
import com.akobor.kuvasz.util.getCurrentTimestamp
import io.micronaut.http.HttpStatus
import java.net.URI
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
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

@ExperimentalTime
fun UptimeMonitorEvent.getEndedEventDuration(): Option<Duration> =
    previousEvent.flatMap { previousEvent ->
        Option.fromNullable(
            if (toUptimeStatus() != previousEvent.status) {
                val diff = dispatchedAt.toEpochSecond() - previousEvent.startedAt.toEpochSecond()
                diff.toDuration(DurationUnit.SECONDS)
            } else null
        )
    }
