package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DnsMonitorSchema(
    val id: Long,
    val name: String,
    val host: String,
    val resolverHost: String?,
    val resolverPort: Int,
    val transport: DnsTransport,
    val recordMatchers: List<DnsRecordMatcher>,
    val expectedResponseCode: DnsResponseCode,
    val driftDetectionEnabled: Boolean,
    val driftRecordTypes: List<DnsRecordType>,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long,
    val metricsHistoryEnabled: Boolean,
    val enabled: Boolean,
    val integrations: Set<String>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val category: String?,
) {
    companion object {
        fun fromDto(dto: DnsMonitorDto) = DnsMonitorSchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            resolverHost = dto.resolverHost,
            resolverPort = dto.resolverPort,
            transport = dto.transport,
            recordMatchers = dto.recordMatchers,
            expectedResponseCode = dto.expectedResponseCode,
            driftDetectionEnabled = dto.driftDetectionEnabled,
            driftRecordTypes = dto.driftRecordTypes,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            timeoutMs = dto.timeoutMs,
            latencyThresholdMs = dto.latencyThresholdMs,
            failureCountThreshold = dto.failureCountThreshold,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            enabled = dto.enabled,
            integrations = dto.integrations.map { it.toString() }.toSet(),
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            category = dto.category,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DnsMonitorDetailsSchema(
    val id: Long,
    val name: String,
    val host: String,
    val resolverHost: String?,
    val resolverPort: Int,
    val transport: DnsTransport,
    val recordMatchers: List<DnsRecordMatcher>,
    val expectedResponseCode: DnsResponseCode,
    val driftDetectionEnabled: Boolean,
    val driftRecordTypes: List<DnsRecordType>,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long,
    val metricsHistoryEnabled: Boolean,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val category: String?,
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
        fun fromDto(dto: DnsMonitorDetailsDto) = DnsMonitorDetailsSchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            resolverHost = dto.resolverHost,
            resolverPort = dto.resolverPort,
            transport = dto.transport,
            recordMatchers = dto.recordMatchers,
            expectedResponseCode = dto.expectedResponseCode,
            driftDetectionEnabled = dto.driftDetectionEnabled,
            driftRecordTypes = dto.driftRecordTypes,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            timeoutMs = dto.timeoutMs,
            latencyThresholdMs = dto.latencyThresholdMs,
            failureCountThreshold = dto.failureCountThreshold,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            enabled = dto.enabled,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            category = dto.category,
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
data class DnsMonitorSummarySchema(
    val id: Long,
    val name: String,
    val host: String,
    val transport: DnsTransport,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val uptimeError: String?,
) {
    companion object {
        fun fromDto(dto: DnsMonitorDetailsDto) = DnsMonitorSummarySchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            transport = dto.transport,
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
data class DnsMonitorListSchema(
    val monitors: List<DnsMonitorSummarySchema>,
)

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class DnsMonitorStatsSchema(
    val id: Long,
    val metricsHistoryEnabled: Boolean,
    val uptimeHistory: HistoricalUptimeStatsSchema,
    val latencyStats: LatencyStatsSchema?,
    val metricsLogs: List<DnsMetricsLogSchema>,
) {
    companion object {
        fun fromDto(dto: DnsMonitorStatsDto) = DnsMonitorStatsSchema(
            id = dto.id,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            uptimeHistory = HistoricalUptimeStatsSchema.fromDto(dto.uptimeHistory),
            latencyStats = dto.latencyStats?.let { LatencyStatsSchema.fromDto(it) },
            metricsLogs = dto.metricsLogs.map { DnsMetricsLogSchema.fromDto(it) },
        )
    }
}

@JsonSchema
@Introspected
data class DnsMonitorCreatorSchema(
    @get:NotBlank
    val name: String,
    @get:NotBlank
    val host: String,
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int,
    val resolverHost: String?,
    @get:Min(Validation.MIN_PORT)
    @get:Max(Validation.MAX_PORT)
    val resolverPort: Int?,
    val transport: DnsTransport?,
    val recordMatchers: List<DnsRecordMatcher>?,
    val expectedResponseCode: DnsResponseCode?,
    val driftDetectionEnabled: Boolean?,
    val driftRecordTypes: List<DnsRecordType>?,
    val timeoutMs: Int?,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long?,
    val category: String? = null,
    val enabled: Boolean?,
    val integrations: List<String>?,
    val metricsHistoryEnabled: Boolean?,
) {
    fun toDto() = DnsMonitorCreateDto(
        name = name,
        host = host,
        uptimeCheckInterval = uptimeCheckInterval,
        resolverHost = resolverHost,
        resolverPort = resolverPort ?: DnsMonitorDefaults.RESOLVER_PORT,
        transport = transport ?: DnsTransport.UDP,
        recordMatchers = recordMatchers.orEmpty(),
        expectedResponseCode = expectedResponseCode ?: DnsResponseCode.NOERROR,
        driftDetectionEnabled = driftDetectionEnabled ?: DnsMonitorDefaults.DRIFT_DETECTION_ENABLED,
        driftRecordTypes = driftRecordTypes.orEmpty(),
        timeoutMs = timeoutMs ?: DnsMonitorDefaults.TIMEOUT_MS,
        latencyThresholdMs = latencyThresholdMs,
        failureCountThreshold = failureCountThreshold ?: DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD,
        category = category,
        enabled = enabled ?: DnsMonitorDefaults.MONITOR_ENABLED,
        integrations = integrations.orEmpty(),
        metricsHistoryEnabled = metricsHistoryEnabled ?: DnsMonitorDefaults.METRICS_HISTORY_ENABLED,
    )
}
