package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorStatsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TcpMonitorSchema(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long,
    val metricsHistoryEnabled: Boolean,
    val enabled: Boolean,
    val integrations: Set<String>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: TcpMonitorDto) = TcpMonitorSchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            port = dto.port,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            timeoutMs = dto.timeoutMs,
            latencyThresholdMs = dto.latencyThresholdMs,
            failureCountThreshold = dto.failureCountThreshold,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            enabled = dto.enabled,
            integrations = dto.integrations.map { it.toString() }.toSet(),
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TcpMonitorDetailsSchema(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long,
    val metricsHistoryEnabled: Boolean,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val lastUptimeCheck: OffsetDateTime?,
    val nextUptimeCheck: OffsetDateTime?,
    val uptimeError: String?,
    val integrations: Set<String>,
    val statusPages: Set<String>,
    val inMaintenance: Boolean,
    val maintenanceWindows: List<MaintenanceWindowSummarySchema>,
) {
    companion object {
        fun fromDto(dto: TcpMonitorDetailsDto) = TcpMonitorDetailsSchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            port = dto.port,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            timeoutMs = dto.timeoutMs,
            latencyThresholdMs = dto.latencyThresholdMs,
            failureCountThreshold = dto.failureCountThreshold,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            enabled = dto.enabled,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            uptimeStatus = dto.uptimeStatus,
            uptimeStatusStartedAt = dto.uptimeStatusStartedAt,
            lastUptimeCheck = dto.lastUptimeCheck,
            nextUptimeCheck = dto.nextUptimeCheck,
            uptimeError = dto.uptimeError,
            integrations = dto.integrations.map { it.toString() }.toSet(),
            statusPages = dto.statusPages,
            inMaintenance = dto.inMaintenance,
            maintenanceWindows = dto.maintenanceWindows.map { MaintenanceWindowSummarySchema.fromDto(it) },
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TcpMonitorSummarySchema(
    val id: Long,
    val name: String,
    val host: String,
    val port: Int,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val uptimeError: String?,
) {
    companion object {
        fun fromDto(dto: TcpMonitorDetailsDto) = TcpMonitorSummarySchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            port = dto.port,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            enabled = dto.enabled,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            uptimeStatus = dto.uptimeStatus,
            uptimeStatusStartedAt = dto.uptimeStatusStartedAt,
            uptimeError = dto.uptimeError,
        )
    }
}

@JsonSchema
@Introspected
data class TcpMonitorListSchema(
    val monitors: List<TcpMonitorSummarySchema>,
)

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TcpMonitorStatsSchema(
    val id: Long,
    val metricsHistoryEnabled: Boolean,
    val uptimeHistory: HistoricalUptimeStatsSchema,
    val latencyStats: LatencyStatsSchema?,
    val metricsLogs: List<TcpMetricsLogSchema>,
) {
    companion object {
        fun fromDto(dto: TcpMonitorStatsDto) = TcpMonitorStatsSchema(
            id = dto.id,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            uptimeHistory = HistoricalUptimeStatsSchema.fromDto(dto.uptimeHistory),
            latencyStats = dto.latencyStats?.let { LatencyStatsSchema.fromDto(it) },
            metricsLogs = dto.metricsLogs.map { TcpMetricsLogSchema.fromDto(it) },
        )
    }
}

@JsonSchema
@Introspected
data class TcpMonitorCreatorSchema(
    @get:NotBlank
    val name: String,
    @get:NotBlank
    val host: String,
    @get:Min(Validation.MIN_PORT)
    @get:Max(Validation.MAX_PORT)
    val port: Int,
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int,
    val timeoutMs: Int?,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long?,
    val enabled: Boolean?,
    val integrations: List<String>?,
    val metricsHistoryEnabled: Boolean?,
) {
    fun toDto() = TcpMonitorCreateDto(
        name = name,
        host = host,
        port = port,
        uptimeCheckInterval = uptimeCheckInterval,
        timeoutMs = timeoutMs ?: TcpMonitorDefaults.TIMEOUT_MS,
        latencyThresholdMs = latencyThresholdMs,
        failureCountThreshold = failureCountThreshold ?: TcpMonitorDefaults.FAILURE_COUNT_THRESHOLD,
        enabled = enabled ?: TcpMonitorDefaults.MONITOR_ENABLED,
        integrations = integrations.orEmpty(),
        metricsHistoryEnabled = metricsHistoryEnabled ?: TcpMonitorDefaults.METRICS_HISTORY_ENABLED
    )
}
