package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.monitor.CheckPushMonitorsWritable
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.PushMonitorSummarySchema
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDto
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton

@Singleton
class PushMonitorTools(
    private val pushMonitorActions: PushMonitorActions,
    private val validator: Validator,
) {

    @Tool(
        name = ToolNames.LIST_PUSH_MONITORS,
        description = "Lists all push (heartbeat) monitors configured in Kuvasz with their current uptime status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listPushMonitors(): PushMonitorListSchema =
        PushMonitorListSchema(
            monitors = pushMonitorActions.getMonitorsWithDetails().map { PushMonitorSummarySchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_PUSH_MONITOR_DETAILS,
        description = "Get detailed information about a specific push (heartbeat) monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getPushMonitorDetails(
        @ToolArg(description = "The numeric ID of the push monitor") monitorId: Long,
    ): PushMonitorDetailsSchema =
        PushMonitorDetailsSchema.fromDto(pushMonitorActions.getMonitorDetails(monitorId))

    @Tool(
        name = ToolNames.CREATE_PUSH_MONITOR,
        description = "Creates a new push (heartbeat) monitor. 'name', 'heartbeatInterval', and 'clientSecret'" +
            " are required; all other fields use sensible defaults.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckPushMonitorsWritable
    fun createPushMonitor(input: PushMonitorCreatorSchema): PushMonitorSchema {
        val creatorDto = input.toDto()
        validator.validate(creatorDto).throwIfNotEmpty()
        return PushMonitorSchema.fromDto(PushMonitorDto.fromMonitorRecord(pushMonitorActions.createMonitor(creatorDto)))
    }

    @Tool(
        name = ToolNames.GET_PUSH_MONITOR_STATS,
        description = "Get uptime statistics for a specific push (heartbeat) monitor.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getPushMonitorStats(
        @ToolArg(description = "The numeric ID of the push monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: String? = null,
    ): PushMonitorStatsSchema {
        val stats = pushMonitorActions.getMonitorStats(
            monitorId = monitorId,
            period = period.asDuration() ?: DEFAULT_STATS_PERIOD,
        )
        return PushMonitorStatsSchema.fromDto(stats)
    }

    @Tool(
        name = ToolNames.TOGGLE_PUSH_MONITOR,
        description = "Enables or disables a push (heartbeat) monitor.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    @CheckPushMonitorsWritable
    fun togglePushMonitor(
        @ToolArg(description = "The numeric ID of the push monitor") monitorId: Long,
        @ToolArg(description = "Set to true to enable the monitor, false to disable it") enabled: Boolean,
    ) = PushMonitorSchema.fromDto(
        PushMonitorDto.fromMonitorRecord(
            pushMonitorActions.updateMonitor(monitorId, monitorToggleUpdate(enabled))
        )
    )
}
