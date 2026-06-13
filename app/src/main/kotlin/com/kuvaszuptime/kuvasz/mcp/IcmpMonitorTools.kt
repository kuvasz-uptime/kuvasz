package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.monitor.CheckIcmpMonitorsWritable
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IcmpMonitorSummarySchema
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDto
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton

@Singleton
class IcmpMonitorTools(
    private val icmpMonitorActions: IcmpMonitorActions,
    private val validator: Validator,
) {

    @Tool(
        name = ToolNames.LIST_ICMP_MONITORS,
        description = "Lists all ICMP (ping) monitors configured in Kuvasz with their current uptime status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listIcmpMonitors(): IcmpMonitorListSchema =
        IcmpMonitorListSchema(
            monitors = icmpMonitorActions.getMonitorsWithDetails().map { IcmpMonitorSummarySchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_ICMP_MONITOR_DETAILS,
        description = "Get detailed information about a specific ICMP (ping) monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getIcmpMonitorDetails(
        @ToolArg(description = "The numeric ID of the ICMP monitor") monitorId: Long,
    ): IcmpMonitorDetailsSchema =
        IcmpMonitorDetailsSchema.fromDto(icmpMonitorActions.getMonitorDetails(monitorId))

    @Tool(
        name = ToolNames.GET_ICMP_MONITOR_STATS,
        description = "Get latency and packet-loss statistics for a specific ICMP (ping) monitor, " +
            "plus uptime history.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getIcmpMonitorStats(
        @ToolArg(description = "The numeric ID of the ICMP monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: String? = null,
    ): IcmpMonitorStatsSchema {
        val stats = icmpMonitorActions.getMonitorStats(monitorId, period.asDuration() ?: DEFAULT_STATS_PERIOD)
        return IcmpMonitorStatsSchema.fromDto(stats)
    }

    @Tool(
        name = ToolNames.CREATE_ICMP_MONITOR,
        description = "Creates a new ICMP monitor. Only 'name', 'host', and 'uptimeCheckInterval'" +
            " are required; all other fields use sensible defaults." +
            "Refer to the docs for the default values: https://kuvasz-uptime.dev/management/icmp-monitors/." +
            "The available integrations can be found via the $LIST_INTEGRATIONS tool." +
            "This tool will work only if 'areIcmpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckIcmpMonitorsWritable
    fun createIcmpMonitor(input: IcmpMonitorCreatorSchema): IcmpMonitorSchema {
        val creatorDto = input.toDto()
        validator.validate(creatorDto).throwIfNotEmpty()
        return IcmpMonitorSchema.fromDto(IcmpMonitorDto.fromMonitorRecord(icmpMonitorActions.createMonitor(creatorDto)))
    }

    @Tool(
        name = ToolNames.TOGGLE_ICMP_MONITOR,
        description = "Enables or disables an ICMP (ping) monitor." +
            "This tool will work only if 'areIcmpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    @CheckIcmpMonitorsWritable
    fun toggleIcmpMonitor(
        @ToolArg(description = "The numeric ID of the ICMP monitor") monitorId: Long,
        @ToolArg(description = "Set to true to enable the monitor, false to disable it") enabled: Boolean,
    ): IcmpMonitorSchema =
        IcmpMonitorSchema.fromDto(
            IcmpMonitorDto.fromMonitorRecord(
                icmpMonitorActions.updateMonitor(monitorId, monitorToggleUpdate(enabled))
            )
        )

    @Tool(
        name = ToolNames.DELETE_ICMP_MONITOR,
        description = "Permanently deletes an ICMP (ping) monitor by its ID, including all its history and events. " +
            "This tool will work only if 'areIcmpMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error. " +
            "It will also fail if the monitor is referenced by a read-only status page.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    @CheckIcmpMonitorsWritable
    fun deleteIcmpMonitor(
        @ToolArg(description = "The numeric ID of the ICMP monitor") monitorId: Long,
    ): DeleteResultSchema {
        icmpMonitorActions.deleteMonitorById(monitorId)
        return DeleteResultSchema(deleted = true, id = monitorId)
    }
}
