package com.kuvaszuptime.kuvasz.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.PersistenceException
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDto
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import jakarta.inject.Singleton
import java.time.Duration

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
    fun listPushMonitors(): CallToolResult =
        success(mapOf("monitors" to pushMonitorActions.getMonitorsWithDetails()))

    @Tool(
        name = ToolNames.GET_PUSH_MONITOR_DETAILS,
        description = "Get detailed information about a specific push (heartbeat) monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getPushMonitorDetails(
        @ToolArg(description = "The numeric ID of the push monitor") monitorId: Long,
    ): CallToolResult =
        try {
            success(pushMonitorActions.getMonitorDetails(monitorId))
        } catch (_: MonitorNotFoundException) {
            error("Push monitor with ID $monitorId not found")
        }

    @Tool(
        name = ToolNames.GET_PUSH_MONITOR_STATS,
        description = "Get uptime statistics for a specific push (heartbeat) monitor.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getPushMonitorStats(
        @ToolArg(description = "The numeric ID of the push monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: Duration? = null,
    ): CallToolResult =
        try {
            success(pushMonitorActions.getMonitorStats(monitorId, period ?: DEFAULT_STATS_PERIOD))
        } catch (_: MonitorNotFoundException) {
            error("Push monitor with ID $monitorId not found")
        }

    @Tool(
        name = ToolNames.UPDATE_PUSH_MONITOR,
        description = "Partially updates a push (heartbeat) monitor. Only fields included in the arguments " +
            "are changed — omitted fields keep their current values. " +
            "Required: monitorId (Long). " +
            "Updatable fields: name, heartbeatInterval, gracePeriod, clientSecret, enabled, " +
            "integrations, failureCountThreshold.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    fun updatePushMonitor(request: McpSchema.CallToolRequest): CallToolResult {
        if (appConfig.isPushMonitorExternalWriteDisabled()) {
            return error("Updating push monitors is currently disabled because they were configured via YAML")
        }
        val (monitorId, updates) = extractMonitorIdAndUpdates(request) ?: return error("monitorId is required")
        return try {
            success(PushMonitorDto.fromMonitorRecord(pushMonitorActions.updateMonitor(monitorId, updates)))
        } catch (_: MonitorNotFoundException) {
            error("Push monitor with ID $monitorId not found")
        } catch (e: PersistenceException) {
            error(e.message.orEmpty())
        }
    }
}
