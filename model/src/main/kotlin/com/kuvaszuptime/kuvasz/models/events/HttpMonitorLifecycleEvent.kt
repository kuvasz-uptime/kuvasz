package com.kuvaszuptime.kuvasz.models.events

sealed interface HttpMonitorLifecycleEvent {
    val monitorId: Long
}

data class HttpMonitorUpdateEvent(override val monitorId: Long) : HttpMonitorLifecycleEvent

data class HttpMonitorDeleteEvent(override val monitorId: Long) : HttpMonitorLifecycleEvent
