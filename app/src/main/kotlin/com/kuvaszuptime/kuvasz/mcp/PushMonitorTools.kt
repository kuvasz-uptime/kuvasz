package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mcp.models.PushMonitorDetailsListSchema
import com.kuvaszuptime.kuvasz.mcp.models.PushMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.models.PushMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.models.PushMonitorStatsSchema
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDto
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import jakarta.inject.Singleton
import tools.jackson.databind.ObjectMapper

@Singleton
class PushMonitorTools(
    private val pushMonitorActions: PushMonitorActions,
    objectMapper: ObjectMapper,
    private val appConfig: AppConfig,
) : KuvaszTools(objectMapper) {

    @Tool(
        name = ToolNames.LIST_PUSH_MONITORS,
        description = "Lists all push (heartbeat) monitors configured in Kuvasz with their current uptime status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listPushMonitors(): PushMonitorDetailsListSchema =
        PushMonitorDetailsListSchema(
            monitors = pushMonitorActions.getMonitorsWithDetails().map { PushMonitorDetailsSchema.fromDto(it) }
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
    fun createPushMonitor(input: PushMonitorCreateDto): PushMonitorSchema {
        appConfig.checkMonitorMutability(MonitorType.PUSH)
        return PushMonitorSchema.fromDto(PushMonitorDto.fromMonitorRecord(pushMonitorActions.createMonitor(input)))
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
    fun togglePushMonitor(
        @ToolArg(description = "The numeric ID of the push monitor") monitorId: Long,
        @ToolArg(description = "Set to true to enable the monitor, false to disable it") enabled: Boolean,
    ): PushMonitorSchema {
        appConfig.checkMonitorMutability(MonitorType.PUSH)
        return PushMonitorSchema.fromDto(
            PushMonitorDto.fromMonitorRecord(
                pushMonitorActions.updateMonitor(monitorId, enabledPatch(enabled))
            )
        )
    }
}
