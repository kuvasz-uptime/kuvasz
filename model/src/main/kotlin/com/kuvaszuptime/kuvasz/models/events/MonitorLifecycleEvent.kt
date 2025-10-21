package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID

sealed interface MonitorLifecycleEvent {
    val monitor: NumericMonitorID
}

data class MonitorUpdateEvent(override val monitor: NumericMonitorID) : MonitorLifecycleEvent

data class MonitorDeleteEvent(override val monitor: NumericMonitorID) : MonitorLifecycleEvent
