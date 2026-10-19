package com.kuvaszuptime.kuvasz.controllers.internal

import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.http.annotation.Get

interface InternalMonitorOperations {

    @Get
    fun getMonitors(): List<MonitorID>
}
