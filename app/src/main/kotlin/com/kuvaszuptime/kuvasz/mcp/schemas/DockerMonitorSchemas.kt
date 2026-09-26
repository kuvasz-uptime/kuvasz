package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.DockerMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.CpuUsageStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.MemoryUsageStatsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import java.time.OffsetDateTime

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DockerMonitorSchema(
    val id: Long,
    val name: String,
    val dockerHost: String,
    val container: String,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val failureCountThreshold: Long,
    val metricsHistoryEnabled: Boolean,
    val enabled: Boolean,
    val integrations: Set<String>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val category: String?,
    val ignoreConnectivityCheck: Boolean,
) {
    companion object {
        fun fromDto(dto: DockerMonitorDto) = DockerMonitorSchema(
            id = dto.id,
            name = dto.name,
            dockerHost = dto.dockerHost,
            container = dto.container,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            timeoutMs = dto.timeoutMs,
            failureCountThreshold = dto.failureCountThreshold,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            enabled = dto.enabled,
            integrations = dto.integrations.map { it.toString() }.toSet(),
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            category = dto.category,
            ignoreConnectivityCheck = dto.ignoreConnectivityCheck,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DockerMonitorDetailsSchema(
    val id: Long,
    val name: String,
    val dockerHost: String,
    val container: String,
    val image: String?,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val failureCountThreshold: Long,
    val metricsHistoryEnabled: Boolean,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val category: String?,
    val ignoreConnectivityCheck: Boolean,
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
        fun fromDto(dto: DockerMonitorDetailsDto) = DockerMonitorDetailsSchema(
            id = dto.id,
            name = dto.name,
            dockerHost = dto.dockerHost,
            container = dto.container,
            image = dto.image,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            timeoutMs = dto.timeoutMs,
            failureCountThreshold = dto.failureCountThreshold,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            enabled = dto.enabled,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            category = dto.category,
            ignoreConnectivityCheck = dto.ignoreConnectivityCheck,
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
data class DockerMonitorSummarySchema(
    val id: Long,
    val name: String,
    val dockerHost: String,
    val container: String,
    val image: String?,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val uptimeError: String?,
    val category: String?,
    val ignoreConnectivityCheck: Boolean,
) {
    companion object {
        fun fromDto(dto: DockerMonitorDetailsDto) = DockerMonitorSummarySchema(
            id = dto.id,
            name = dto.name,
            dockerHost = dto.dockerHost,
            container = dto.container,
            image = dto.image,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            enabled = dto.enabled,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            uptimeStatus = dto.uptimeStatus,
            uptimeStatusStartedAt = dto.uptimeStatusStartedAt,
            uptimeError = dto.uptimeError,
            category = dto.category,
            ignoreConnectivityCheck = dto.ignoreConnectivityCheck,
        )
    }
}

@JsonSchema
@Introspected
data class DockerMonitorListSchema(
    val monitors: List<DockerMonitorSummarySchema>,
)

/**
 * Reports the container's own resource use rather than a latency: the only timing a Docker check produces measures
 * the daemon, not the container, so it is deliberately absent here.
 */
@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DockerMonitorStatsSchema(
    val id: Long,
    val metricsHistoryEnabled: Boolean,
    val uptimeHistory: HistoricalUptimeStatsSchema,
    val cpuStats: CpuUsageStatsSchema?,
    val memoryStats: MemoryUsageStatsSchema?,
    val metricsLogs: List<DockerMetricsLogSchema>,
) {
    companion object {
        fun fromDto(dto: DockerMonitorStatsDto) = DockerMonitorStatsSchema(
            id = dto.id,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            uptimeHistory = HistoricalUptimeStatsSchema.fromDto(dto.uptimeHistory),
            cpuStats = dto.cpuStats?.let { CpuUsageStatsSchema.fromDto(it) },
            memoryStats = dto.memoryStats?.let { MemoryUsageStatsSchema.fromDto(it) },
            metricsLogs = dto.metricsLogs.map { DockerMetricsLogSchema.fromDto(it) },
        )
    }
}

@JsonSchema
@Introspected
data class DockerMonitorCreatorSchema(
    @get:NotBlank
    val name: String,
    @get:NotBlank
    val dockerHost: String,
    @get:NotBlank
    val container: String,
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int,
    @get:Min(Validation.MIN_TIMEOUT_MILLIS)
    @get:Max(Validation.MAX_TIMEOUT_MILLIS)
    val timeoutMs: Int?,
    val failureCountThreshold: Long?,
    val category: String? = null,
    val ignoreConnectivityCheck: Boolean?,
    val enabled: Boolean?,
    val integrations: List<String>?,
    val metricsHistoryEnabled: Boolean?,
) {
    fun toDto() = DockerMonitorCreateDto(
        name = name,
        dockerHost = dockerHost,
        container = container,
        uptimeCheckInterval = uptimeCheckInterval,
        timeoutMs = timeoutMs ?: DockerMonitorDefaults.TIMEOUT_MS,
        failureCountThreshold = failureCountThreshold ?: DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD,
        category = category,
        ignoreConnectivityCheck = ignoreConnectivityCheck ?: DockerMonitorDefaults.IGNORE_CONNECTIVITY_CHECK,
        enabled = enabled ?: DockerMonitorDefaults.MONITOR_ENABLED,
        integrations = integrations.orEmpty(),
        metricsHistoryEnabled = metricsHistoryEnabled ?: DockerMonitorDefaults.METRICS_HISTORY_ENABLED,
    )
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CpuUsageStatsSchema(
    val averageCpuUsagePercentage: BigDecimal?,
    val minCpuUsagePercentage: BigDecimal?,
    val maxCpuUsagePercentage: BigDecimal?,
) {
    companion object {
        fun fromDto(dto: CpuUsageStatsDto) = CpuUsageStatsSchema(
            averageCpuUsagePercentage = dto.averageCpuUsagePercentage,
            minCpuUsagePercentage = dto.minCpuUsagePercentage,
            maxCpuUsagePercentage = dto.maxCpuUsagePercentage,
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MemoryUsageStatsSchema(
    val averageMemoryUsageBytes: Long?,
    val minMemoryUsageBytes: Long?,
    val maxMemoryUsageBytes: Long?,
) {
    companion object {
        fun fromDto(dto: MemoryUsageStatsDto) = MemoryUsageStatsSchema(
            averageMemoryUsageBytes = dto.averageMemoryUsageBytes,
            minMemoryUsageBytes = dto.minMemoryUsageBytes,
            maxMemoryUsageBytes = dto.maxMemoryUsageBytes,
        )
    }
}
