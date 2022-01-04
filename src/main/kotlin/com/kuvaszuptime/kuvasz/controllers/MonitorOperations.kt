package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.*
import io.micronaut.http.annotation.*
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter

interface MonitorOperations {

    @Operation(summary = "Returns all monitors with their details")
    @Get("/")
    fun getMonitorsWithDetails(
        @QueryValue
        @Parameter(required = false)
        enabledOnly: Boolean?
    ): List<MonitorDetailsDto>

    @Operation(summary = "Returns a monitor's details")
    @Get("/{monitorId}")
    fun getMonitorDetails(monitorId: Int): MonitorDetailsDto

    @Operation(summary = "Creates a monitor")
    @Post("/")
    fun createMonitor(@Body monitor: MonitorCreateDto): MonitorDto

    @Operation(summary = "Deletes a monitor by ID")
    @Delete("/{monitorId}")
    fun deleteMonitor(monitorId: Int)

    @Operation(summary = "Updates a monitor by ID")
    @Patch("/{monitorId}")
    fun updateMonitor(monitorId: Int, @Body monitorUpdateDto: MonitorUpdateDto): MonitorDto

    @Operation(summary = "Updates or creates a Pagerduty integration key for the given monitor")
    @Put("/{monitorId}/pagerduty-integration-key")
    fun upsertPagerdutyIntegrationKey(monitorId: Int, @Body upsertDto: PagerdutyKeyUpdateDto): MonitorDto

    @Operation(summary = "Deletes the Pagerduty integration key of the given monitor")
    @Delete("/{monitorId}/pagerduty-integration-key")
    fun deletePagerdutyIntegrationKey(monitorId: Int)

    @Operation(summary = "Returns the uptime events of the given monitor")
    @Get("/{monitorId}/uptime-events")
    fun getUptimeEvents(monitorId: Int): List<UptimeEventDto>

    @Operation(summary = "Returns the SSL events of the given monitor")
    @Get("/{monitorId}/ssl-events")
    fun getSSLEvents(monitorId: Int): List<SSLEventDto>
}
