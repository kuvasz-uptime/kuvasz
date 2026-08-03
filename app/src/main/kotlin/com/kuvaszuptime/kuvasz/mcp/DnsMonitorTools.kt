package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.monitor.CheckDnsMonitorsWritable
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorStatsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DnsMonitorSummarySchema
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorDto
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton

@Singleton
class DnsMonitorTools(
    private val dnsMonitorActions: DnsMonitorActions,
    private val validator: Validator,
) {

    @Tool(
        name = ToolNames.LIST_DNS_MONITORS,
        description = "Lists all DNS monitors configured in Kuvasz with their current uptime status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listDnsMonitors(): DnsMonitorListSchema =
        DnsMonitorListSchema(
            monitors = dnsMonitorActions.getMonitorsWithDetails().map { DnsMonitorSummarySchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_DNS_MONITOR_DETAILS,
        description = "Get detailed information about a specific DNS monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getDnsMonitorDetails(
        @ToolArg(description = "The numeric ID of the DNS monitor") monitorId: Long,
    ): DnsMonitorDetailsSchema =
        DnsMonitorDetailsSchema.fromDto(dnsMonitorActions.getMonitorDetails(monitorId))

    @Tool(
        name = ToolNames.GET_DNS_MONITOR_STATS,
        description = "Get resolution-latency statistics for a specific DNS monitor, plus uptime history.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getDnsMonitorStats(
        @ToolArg(description = "The numeric ID of the DNS monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: String? = null,
    ): DnsMonitorStatsSchema {
        val stats = dnsMonitorActions.getMonitorStats(monitorId, period.asDuration() ?: DEFAULT_STATS_PERIOD)
        return DnsMonitorStatsSchema.fromDto(stats)
    }

    @Tool(
        name = ToolNames.CREATE_DNS_MONITOR,
        description = "Creates a new DNS monitor. Only 'name', 'host', and 'uptimeCheckInterval' are required;" +
            " all other fields use sensible defaults." +
            "Refer to the docs for the default values and the record-matcher model: " +
            "https://kuvasz-uptime.dev/management/dns-monitors/." +
            "The available integrations can be found via the $LIST_INTEGRATIONS tool." +
            "This tool will work only if 'areDnsMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckDnsMonitorsWritable
    fun createDnsMonitor(input: DnsMonitorCreatorSchema): DnsMonitorSchema {
        val creatorDto = input.toDto()
        validator.validate(creatorDto).throwIfNotEmpty()
        return DnsMonitorSchema.fromDto(DnsMonitorDto.fromMonitorRecord(dnsMonitorActions.createMonitor(creatorDto)))
    }

    @Tool(
        name = ToolNames.TOGGLE_DNS_MONITOR,
        description = "Enables or disables a DNS monitor." +
            "This tool will work only if 'areDnsMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    @CheckDnsMonitorsWritable
    fun toggleDnsMonitor(
        @ToolArg(description = "The numeric ID of the DNS monitor") monitorId: Long,
        @ToolArg(description = "Set to true to enable the monitor, false to disable it") enabled: Boolean,
    ): DnsMonitorSchema =
        DnsMonitorSchema.fromDto(
            DnsMonitorDto.fromMonitorRecord(
                dnsMonitorActions.updateMonitor(monitorId, monitorToggleUpdate(enabled))
            )
        )

    @Tool(
        name = ToolNames.DELETE_DNS_MONITOR,
        description = "Permanently deletes a DNS monitor by its ID, including all its history and events. " +
            "This tool will work only if 'areDnsMonitorsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error. " +
            "It will also fail if the monitor is referenced by a read-only status page.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    @CheckDnsMonitorsWritable
    fun deleteDnsMonitor(
        @ToolArg(description = "The numeric ID of the DNS monitor") monitorId: Long,
    ): DeleteResultSchema {
        dnsMonitorActions.deleteMonitorById(monitorId)
        return DeleteResultSchema(deleted = true, id = monitorId)
    }
}
