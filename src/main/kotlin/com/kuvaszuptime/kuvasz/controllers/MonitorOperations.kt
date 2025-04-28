package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.PagerdutyKeyUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
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
    fun getMonitorDetails(monitorId: Long): MonitorDetailsDto

    @Operation(summary = "Creates a monitor")
    @Post("/")
    fun createMonitor(@Body monitor: MonitorCreateDto): MonitorDto

    @Operation(summary = "Deletes a monitor by ID")
    @Delete("/{monitorId}")
    fun deleteMonitor(monitorId: Long)

    @Operation(summary = "Updates a monitor by ID")
    @Patch("/{monitorId}")
    fun updateMonitor(monitorId: Long, @Body monitorUpdateDto: MonitorUpdateDto): MonitorDto

    @Operation(summary = "Updates or creates a Pagerduty integration key for the given monitor")
    @Put("/{monitorId}/pagerduty-integration-key")
    fun upsertPagerdutyIntegrationKey(monitorId: Long, @Body upsertDto: PagerdutyKeyUpdateDto): MonitorDto

    @Operation(summary = "Deletes the Pagerduty integration key of the given monitor")
    @Delete("/{monitorId}/pagerduty-integration-key")
    fun deletePagerdutyIntegrationKey(monitorId: Long)

    @Operation(summary = "Returns the uptime events of the given monitor")
    @Get("/{monitorId}/uptime-events")
    fun getUptimeEvents(monitorId: Long): List<UptimeEventDto>

    @Operation(summary = "Returns the SSL events of the given monitor")
    @Get("/{monitorId}/ssl-events")
    fun getSSLEvents(monitorId: Long): List<SSLEventDto>

    @Operation(summary = "Returns the stats of the given monitor")
    @Get("/{monitorId}/stats")
    fun getMonitorStats(monitorId: Long): MonitorStatsDto
}
