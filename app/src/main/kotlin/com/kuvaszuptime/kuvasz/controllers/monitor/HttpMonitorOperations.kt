package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.event.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import tools.jackson.databind.node.ObjectNode
import java.time.Duration

interface HttpMonitorOperations {

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
    ): List<HttpMonitorDetailsDto>

    @Operation(summary = "Get a monitor's details")
    @Get("/{monitorId}")
    fun getMonitorDetails(monitorId: Long): HttpMonitorDetailsDto

    @Operation(summary = "Create a monitor")
    @Post("/")
    fun createMonitor(@Body monitor: HttpMonitorCreateDto): HttpMonitorDto

    @Operation(summary = "Delete a monitor by ID")
    @Delete("/{monitorId}")
    fun deleteMonitor(monitorId: Long)

    @Operation(
        summary = "Update a monitor by ID",
        description = "Updates the monitor with the given ID. Only fields that are present in the request body " +
            "will be updated. Fields not present in the request body will remain unchanged.",
        requestBody = RequestBody(content = [Content(schema = Schema(implementation = HttpMonitorUpdateDto::class))])
    )
    @Patch("/{monitorId}")
    fun updateMonitor(monitorId: Long, @Body updates: ObjectNode): HttpMonitorDto

    @Operation(summary = "Get the uptime events of the given monitor")
    @Get("/{monitorId}/uptime-events")
    fun getUptimeEvents(monitorId: Long): List<HttpUptimeEventDto>

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
            schema = Schema(
                implementation = Duration::class,
                description = "An ISO-8601 Duration string, default P1D",
            )
        )
        period: Duration?,
    ): HttpMonitorStatsDto

    @Operation(summary = "Get the overall, cumulative stats of all monitors")
    @Get("/stats")
    fun getMonitoringStats(
        @QueryValue
        @Parameter(
            required = false,
            schema = Schema(
                implementation = Duration::class,
                description = "An ISO-8601 Duration string, default P7D",
            )
        )
        period: Duration?,
    ): HttpMonitoringStatsDto
}
