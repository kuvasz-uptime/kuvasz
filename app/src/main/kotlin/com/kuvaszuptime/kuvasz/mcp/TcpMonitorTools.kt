package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.monitor.CheckTcpMonitorsWritable
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.TcpMonitorSummarySchema
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorDto
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton

@Singleton
class TcpMonitorTools(
    private val tcpMonitorActions: TcpMonitorActions,
    private val validator: Validator,
) {

    @Tool(
        name = ToolNames.LIST_TCP_MONITORS,
        description = "Lists all TCP port monitors configured in Kuvasz with their current uptime status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listTcpMonitors(): TcpMonitorListSchema =
        TcpMonitorListSchema(
            monitors = tcpMonitorActions.getMonitorsWithDetails().map { TcpMonitorSummarySchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_TCP_MONITOR_DETAILS,
        description = "Get detailed information about a specific TCP port monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getTcpMonitorDetails(
        @ToolArg(description = "The numeric ID of the TCP monitor") monitorId: Long,
    ): TcpMonitorDetailsSchema =
        TcpMonitorDetailsSchema.fromDto(tcpMonitorActions.getMonitorDetails(monitorId))

    @Tool(
        name = ToolNames.GET_TCP_MONITOR_STATS,
        description = "Get connect-latency statistics for a specific TCP port monitor, plus uptime history.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getTcpMonitorStats(
        @ToolArg(description = "The numeric ID of the TCP monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: String? = null,
    ): TcpMonitorStatsSchema {
        val stats = tcpMonitorActions.getMonitorStats(monitorId, period.asDuration() ?: DEFAULT_STATS_PERIOD)
        return TcpMonitorStatsSchema.fromDto(stats)
    }

    @Tool(
        name = ToolNames.CREATE_TCP_MONITOR,
        description = "Creates a new TCP port monitor. Only 'name', 'host', 'port', and 'uptimeCheckInterval'" +
            " are required; all other fields use sensible defaults." +
            "Refer to the docs for the default values: https://kuvasz-uptime.dev/management/tcp-monitors/." +
            "The available integrations can be found via the $LIST_INTEGRATIONS tool." +
            "This tool will work only if 'areTcpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckTcpMonitorsWritable
    fun createTcpMonitor(input: TcpMonitorCreatorSchema): TcpMonitorSchema {
        val creatorDto = input.toDto()
        validator.validate(creatorDto).throwIfNotEmpty()
        return TcpMonitorSchema.fromDto(TcpMonitorDto.fromMonitorRecord(tcpMonitorActions.createMonitor(creatorDto)))
    }

    @Tool(
        name = ToolNames.TOGGLE_TCP_MONITOR,
        description = "Enables or disables a TCP port monitor." +
            "This tool will work only if 'areTcpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    @CheckTcpMonitorsWritable
    fun toggleTcpMonitor(
        @ToolArg(description = "The numeric ID of the TCP monitor") monitorId: Long,
        @ToolArg(description = "Set to true to enable the monitor, false to disable it") enabled: Boolean,
    ): TcpMonitorSchema =
        TcpMonitorSchema.fromDto(
            TcpMonitorDto.fromMonitorRecord(
                tcpMonitorActions.updateMonitor(monitorId, monitorToggleUpdate(enabled))
            )
        )

    @Tool(
        name = ToolNames.DELETE_TCP_MONITOR,
        description = "Permanently deletes a TCP port monitor by its ID, including all its history and events. " +
            "This tool will work only if 'areTcpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error. " +
            "It will also fail if the monitor is referenced by a read-only status page.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    @CheckTcpMonitorsWritable
    fun deleteTcpMonitor(
        @ToolArg(description = "The numeric ID of the TCP monitor") monitorId: Long,
    ): DeleteResultSchema {
        tcpMonitorActions.deleteMonitorById(monitorId)
        return DeleteResultSchema(deleted = true, id = monitorId)
    }
}
