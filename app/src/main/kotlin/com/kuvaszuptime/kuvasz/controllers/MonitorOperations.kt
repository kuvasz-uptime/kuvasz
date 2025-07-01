package com.kuvaszuptime.kuvasz.controllers

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.server.types.files.SystemFile
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import java.time.Duration

interface MonitorOperations {

    @Operation(summary = "Get all monitors with their details")
    @Get("/")
    fun getMonitorsWithDetails(
        @QueryValue
        @Parameter(required = false)
        enabled: Boolean?,
        @QueryValue
        @Parameter(required = false)
        uptimeStatus: List<UptimeStatus>?,
        @QueryValue
        @Parameter(required = false)
        sslStatus: List<SslStatus>?,
        @QueryValue
        @Parameter(required = false)
        sslCheckEnabled: Boolean?,
    ): List<MonitorDetailsDto>

    @Operation(summary = "Get a monitor's details")
    @Get("/{monitorId}")
    fun getMonitorDetails(monitorId: Long): MonitorDetailsDto

    @Operation(summary = "Create a monitor")
    @Post("/")
    fun createMonitor(@Body monitor: MonitorCreateDto): MonitorDto

    @Operation(summary = "Delete a monitor by ID")
    @Delete("/{monitorId}")
    fun deleteMonitor(monitorId: Long)

    @Operation(
        summary = "Update a monitor by ID",
        description = "Updates the monitor with the given ID. Only fields that are present in the request body " +
            "will be updated. Fields not present in the request body will remain unchanged.",
        requestBody = RequestBody(content = [Content(schema = Schema(implementation = MonitorUpdateDto::class))])
    )
    @Patch("/{monitorId}")
    fun updateMonitor(monitorId: Long, @Body updates: ObjectNode): MonitorDto

    @Operation(summary = "Get the uptime events of the given monitor")
    @Get("/{monitorId}/uptime-events")
    fun getUptimeEvents(monitorId: Long): List<UptimeEventDto>

    @Operation(summary = "Get the SSL events of the given monitor")
    @Get("/{monitorId}/ssl-events")
    fun getSSLEvents(monitorId: Long): List<SSLEventDto>

    @Operation(summary = "Get the stats of the given monitor")
    @Get("/{monitorId}/stats")
    fun getMonitorStats(
        monitorId: Long,
        @QueryValue
        @Parameter(
            required = false,
            schema = Schema(implementation = Duration::class, description = "A Java Duration string, default 1d")
        )
        period: Duration?,
    ): MonitorStatsDto

    @Operation(summary = "Download the export of all monitors in YAML format")
    @Get("/export/yaml")
    fun getYamlMonitorsExport(): SystemFile

    @Operation(summary = "Get the overall, cumulative stats of all monitors")
    @Get("/stats")
    fun getMonitoringStats(
        @QueryValue
        @Parameter(
            required = false,
            schema = Schema(implementation = Duration::class, description = "A Java Duration string, default 7d")
        )
        period: Duration?,
    ): MonitoringStatsDto
}
