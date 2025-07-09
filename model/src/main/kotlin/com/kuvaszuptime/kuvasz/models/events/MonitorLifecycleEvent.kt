package com.kuvaszuptime.kuvasz.models.events

sealed interface MonitorLifecycleEvent {
    val monitorId: Long
}

data class MonitorCreateEvent(override val monitorId: Long) : MonitorLifecycleEvent

data class MonitorUpdateEvent(override val monitorId: Long) : MonitorLifecycleEvent

data class MonitorDeleteEvent(override val monitorId: Long) : MonitorLifecycleEvent
