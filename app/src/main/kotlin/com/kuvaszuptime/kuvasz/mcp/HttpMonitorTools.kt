package com.kuvaszuptime.kuvasz.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.mcp.models.HttpMonitorDetailsListSchema
import com.kuvaszuptime.kuvasz.mcp.models.HttpMonitorDetailsSchema
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import java.time.Duration

@Singleton
class HttpMonitorTools(
    private val httpMonitorActions: HttpMonitorActions,
    objectMapper: ObjectMapper,
    private val appConfig: AppConfig,
) : KuvaszTools(objectMapper) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Tool(
        name = ToolNames.LIST_HTTP_MONITORS,
        description = "Lists all HTTP monitors configured in Kuvasz with their current uptime and SSL status",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listHttpMonitors(): HttpMonitorDetailsListSchema {
        logger.info("Listing HTTP monitors...")
        return HttpMonitorDetailsListSchema(
            monitors = httpMonitorActions.getMonitorsWithDetails().map { HttpMonitorDetailsSchema.fromDto(it) }
        )
    }

    @Tool(
        name = ToolNames.GET_HTTP_MONITOR_DETAILS,
        description = "Get detailed information about a specific HTTP monitor by its ID",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getHttpMonitorDetails(
        @ToolArg(description = "The numeric ID of the HTTP monitor") monitorId: Long,
    ): HttpMonitorDetailsDto {
        //        try {
        logger.info("Getting detailed information about HTTP monitor: $monitorId")
        return httpMonitorActions.getMonitorDetails(monitorId)
    }
//        } catch (_: MonitorNotFoundException) {
//            error("Monitor with ID $monitorId not found")
//        }

    @Tool(
        name = ToolNames.CREATE_HTTP_MONITOR,
        description = "Creates a new HTTP monitor. Only 'name', 'url', and 'uptimeCheckInterval'" +
            " are required; all other fields use sensible defaults.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    fun createHttpMonitor(input: HttpMonitorCreateDto): HttpMonitorDto {
        if (appConfig.isHttpMonitorExternalWriteDisabled()) {
            throw ConstraintViolationException(
                "Creating HTTP monitors is currently disabled because they were configured via YAML",
                emptySet(),
            )
        }
//        return try {
        return HttpMonitorDto.fromMonitorRecord(httpMonitorActions.createMonitor(input))
//        } catch (e: PersistenceException) {
//            error(e.message.orEmpty())
//        }
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
        period: Duration? = null,
    ): HttpMonitorStatsDto =
//        try {
        httpMonitorActions.getMonitorStats(monitorId, period ?: DEFAULT_STATS_PERIOD)
//        } catch (_: MonitorNotFoundException) {
//            error("HTTP monitor with ID $monitorId not found")
//        }
}
