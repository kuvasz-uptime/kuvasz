package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import javax.validation.Valid

@Validated
interface MonitorOperations {

    @Operation(summary = "Returns all monitors with their details")
    @Get("/")
    @ExecuteOn(TaskExecutors.IO)
    fun getMonitorsWithDetails(
        @QueryValue
        @Parameter(required = false)
        enabledOnly: Boolean?
    ): List<MonitorDetailsDto>

    @Operation(summary = "Returns a monitor's details")
    @Get("/{monitorId}")
    @ExecuteOn(TaskExecutors.IO)
    fun getMonitorDetails(monitorId: Int): MonitorDetailsDto

    @Operation(summary = "Creates a monitor")
    @Post("/")
    @ExecuteOn(TaskExecutors.IO)
    fun createMonitor(@Valid @Body monitor: MonitorCreateDto): MonitorPojo

    @Operation(summary = "Deletes a monitor by ID")
    @Delete("/{monitorId}")
    @ExecuteOn(TaskExecutors.IO)
    fun deleteMonitor(monitorId: Int)

    @Operation(summary = "Updates a monitor by ID")
    @Patch("/{monitorId}")
    @ExecuteOn(TaskExecutors.IO)
    fun updateMonitor(monitorId: Int, @Valid @Body monitorUpdateDto: MonitorUpdateDto): MonitorPojo
}
