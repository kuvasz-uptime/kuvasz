package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.EventDispatcher

abstract class EventHandlerTest(private val databaseEventHandler: DatabaseEventHandler) : DatabaseBehaviorSpec() {

    fun EventDispatcher.testDispatch(event: UptimeMonitorEvent) {
        databaseEventHandler.handleUptimeMonitorEvent(event)
        dispatch(event)
    }

    fun EventDispatcher.testDispatch(event: SSLMonitorEvent) {
        databaseEventHandler.handleSSLMonitorEvent(event)
        dispatch(event)
    }
}
