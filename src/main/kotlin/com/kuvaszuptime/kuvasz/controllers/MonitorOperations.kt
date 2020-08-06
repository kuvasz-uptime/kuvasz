package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.MonitorDetails
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter

@Validated
interface MonitorOperations {

    @Operation(summary = "Returns all monitors")
    @Get("/")
    @ExecuteOn(TaskExecutors.IO)
    fun getMonitors(
        @QueryValue
        @Parameter(required = false)
        enabledOnly: Boolean?
    ): List<MonitorDetails>

    @Operation(summary = "Returns a monitor's details")
    @Get("/{monitorId}")
    @ExecuteOn(TaskExecutors.IO)
    fun getMonitor(monitorId: Int): MonitorDetails
}
