package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.event.PushUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
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

interface PushMonitorOperations {

    @Operation(summary = "Get all monitors with their details")
    @Get("/")
    fun getMonitorsWithDetails(
        @QueryValue
        @Parameter(required = false)
        enabled: Boolean?,
        @QueryValue
        @Parameter(required = false)
        uptimeStatus: List<UptimeStatus>?,
    ): List<PushMonitorDetailsDto>

    @Operation(summary = "Get a monitor's details")
    @Get("/{monitorId}")
    fun getMonitorDetails(monitorId: Long): PushMonitorDetailsDto

    @Operation(summary = "Create a monitor")
    @Post("/")
    fun createMonitor(@Body monitor: PushMonitorCreateDto): PushMonitorDto

    @Operation(summary = "Delete a monitor by ID")
    @Delete("/{monitorId}")
    fun deleteMonitor(monitorId: Long)

    @Operation(
        summary = "Update a monitor by ID",
        description = "Updates the monitor with the given ID. Only fields that are present in the request body " +
            "will be updated. Fields not present in the request body will remain unchanged.",
        requestBody = RequestBody(content = [Content(schema = Schema(implementation = PushMonitorUpdateDto::class))])
    )
    @Patch("/{monitorId}")
    fun updateMonitor(monitorId: Long, @Body updates: ObjectNode): PushMonitorDto

    @Operation(summary = "Get the uptime events of the given monitor")
    @Get("/{monitorId}/uptime-events")
    fun getUptimeEvents(monitorId: Long): List<PushUptimeEventDto>

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
    ): PushMonitorStatsDto

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
    ): PushMonitoringStatsDto
}
