package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.monitor.CheckHttpMonitorsWritable
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.HttpMonitorSummarySchema
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton

@Singleton
class HttpMonitorTools(
    private val httpMonitorActions: HttpMonitorActions,
    private val validator: Validator,
) {

    @Tool(
        name = ToolNames.LIST_HTTP_MONITORS,
        description =
            "Lists all HTTP monitors configured in Kuvasz with their current uptime, SSL status and basic details",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listHttpMonitors(): HttpMonitorListSchema =
        HttpMonitorListSchema(
            httpMonitorActions.getMonitorsWithDetails().map { HttpMonitorSummarySchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_HTTP_MONITOR_DETAILS,
        description = "Get detailed information about a specific HTTP monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getHttpMonitorDetails(
        @ToolArg(description = "The numeric ID of the HTTP monitor") monitorId: Long,
    ): HttpMonitorDetailsSchema = HttpMonitorDetailsSchema.fromDto(httpMonitorActions.getMonitorDetails(monitorId))

    @Tool(
        name = ToolNames.CREATE_HTTP_MONITOR,
        description = "Creates a new HTTP monitor. Only 'name', 'url', and 'uptimeCheckInterval'" +
            " are required; all other fields use sensible defaults. " +
            "Refer to the docs for the default values: https://kuvasz-uptime.dev/management/http-monitors/." +
            "The available integrations can be found via the $LIST_INTEGRATIONS tool." +
            "This tool will work only if 'areHttpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckHttpMonitorsWritable
    fun createHttpMonitor(input: HttpMonitorCreatorSchema): HttpMonitorSchema {
        val creatorDto = input.toDto()
        validator.validate(creatorDto).throwIfNotEmpty()
        return HttpMonitorSchema.fromDto(HttpMonitorDto.fromMonitorRecord(httpMonitorActions.createMonitor(creatorDto)))
    }

    @Tool(
        name = ToolNames.GET_HTTP_MONITOR_STATS,
        description = "Get latency and uptime statistics for a specific HTTP monitor. " +
            "Includes average, min, max, and percentile latencies plus uptime history.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getHttpMonitorStats(
        @ToolArg(description = "The numeric ID of the HTTP monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: String? = null,
    ): HttpMonitorStatsSchema {
        val effectivePeriod = period.asDuration() ?: DEFAULT_STATS_PERIOD
        val stats = httpMonitorActions.getMonitorStats(monitorId, effectivePeriod)
        return HttpMonitorStatsSchema.fromDto(stats, effectivePeriod)
    }

    @Tool(
        name = ToolNames.TOGGLE_HTTP_MONITOR,
        description = "Enables or disables an HTTP monitor completely. Disabling an HTTP monitor implicitly disables " +
            "the SSL checks of it as well." +
            "This tool will work only if 'areHttpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    @CheckHttpMonitorsWritable
    fun toggleHttpMonitor(
        @ToolArg(description = "The numeric ID of the HTTP monitor") monitorId: Long,
        @ToolArg(description = "Set to true to enable the monitor, false to disable it") enabled: Boolean,
    ): HttpMonitorSchema =
        HttpMonitorSchema.fromDto(
            HttpMonitorDto.fromMonitorRecord(
                httpMonitorActions.updateMonitor(monitorId, monitorToggleUpdate(enabled))
            )
        )

    @Tool(
        name = ToolNames.DELETE_HTTP_MONITOR,
        description = "Permanently deletes an HTTP monitor by its ID, including all its history and events. " +
            "This tool will work only if 'areHttpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error. " +
            "It will also fail if the monitor is referenced by a read-only status page.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    @CheckHttpMonitorsWritable
    fun deleteHttpMonitor(
        @ToolArg(description = "The numeric ID of the HTTP monitor") monitorId: Long,
    ): DeleteResultSchema {
        httpMonitorActions.deleteMonitorById(monitorId)
        return DeleteResultSchema(deleted = true, id = monitorId)
    }
}
