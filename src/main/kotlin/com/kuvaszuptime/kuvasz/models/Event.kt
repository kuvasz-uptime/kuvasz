package com.kuvaszuptime.kuvasz.models

import arrow.core.Option
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.micronaut.http.HttpStatus
import java.net.URI

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
