package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDto
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.modelcontextprotocol.spec.McpSchema.CallToolResult
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import tools.jackson.databind.ObjectMapper
import java.time.Duration

@Singleton
class IcmpMonitorTools(
    private val icmpMonitorActions: IcmpMonitorActions,
    objectMapper: ObjectMapper,
    private val appConfig: AppConfig,
) : KuvaszTools(objectMapper) {

    @Tool(
        name = ToolNames.LIST_ICMP_MONITORS,
        description = "Lists all ICMP (ping) monitors configured in Kuvasz with their current uptime status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listIcmpMonitors(): CallToolResult =
        success(mapOf("monitors" to icmpMonitorActions.getMonitorsWithDetails()))

    @Tool(
        name = ToolNames.GET_ICMP_MONITOR_DETAILS,
        description = "Get detailed information about a specific ICMP (ping) monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getIcmpMonitorDetails(
        @ToolArg(description = "The numeric ID of the ICMP monitor") monitorId: Long,
    ): CallToolResult =
        try {
            success(icmpMonitorActions.getMonitorDetails(monitorId))
        } catch (_: MonitorNotFoundException) {
            error("ICMP monitor with ID $monitorId not found")
        }

    @Tool(
        name = ToolNames.GET_ICMP_MONITOR_STATS,
        description = "Get latency and packet-loss statistics for a specific ICMP (ping) monitor, " +
            "plus uptime history.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getIcmpMonitorStats(
        @ToolArg(description = "The numeric ID of the ICMP monitor") monitorId: Long,
        @ToolArg(description = "ISO 8601 look-back window, e.g. 'P1D' or 'PT12H'. Defaults to P1D.")
        period: Duration? = null,
    ): CallToolResult =
        try {
            success(icmpMonitorActions.getMonitorStats(monitorId, period ?: DEFAULT_STATS_PERIOD))
        } catch (_: MonitorNotFoundException) {
            error("ICMP monitor with ID $monitorId not found")
        }

    @Tool(
        name = ToolNames.CREATE_ICMP_MONITOR,
        description = "Creates a new ICMP monitor. Only 'name', 'host', and 'uptimeCheckInterval'" +
            " are required; all other fields use sensible defaults.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    fun createIcmpMonitor(input: IcmpMonitorCreateDto): IcmpMonitorDto {
        if (appConfig.isIcmpMonitorExternalWriteDisabled()) {
            throw ConstraintViolationException(
                "Creating ICMP monitors is currently disabled because they were configured via YAML",
                emptySet(),
            )
        }
//        return try {
        return IcmpMonitorDto.fromMonitorRecord(icmpMonitorActions.createMonitor(input))
//        } catch (e: PersistenceException) {
//            error(e.message.orEmpty())
//        }
    }
}
