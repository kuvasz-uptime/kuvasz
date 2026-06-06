package com.kuvaszuptime.kuvasz.mcp.models

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorStatsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import java.time.OffsetDateTime

@JsonSchema
@Introspected
data class IcmpMonitorSchema(
    val id: Long,
    val name: String,
    val host: String,
    val uptimeCheckInterval: Int,
    val packetCount: Int,
    val timeoutSeconds: Int,
    val packetLossThreshold: Int,
    val failureCountThreshold: Long,
    val metricsHistoryEnabled: Boolean,
    val enabled: Boolean,
    val integrations: Set<String>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: IcmpMonitorDto) = IcmpMonitorSchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            packetCount = dto.packetCount,
            timeoutSeconds = dto.timeoutSeconds,
            packetLossThreshold = dto.packetLossThreshold,
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
data class IcmpMonitorDetailsSchema(
    val id: Long,
    val name: String,
    val host: String,
    val uptimeCheckInterval: Int,
    val packetCount: Int,
    val timeoutSeconds: Int,
    val packetLossThreshold: Int,
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
    val effectiveIntegrations: Set<IntegrationDetailsSchema>,
    val statusPages: Set<String>,
) {
    companion object {
        fun fromDto(dto: IcmpMonitorDetailsDto) = IcmpMonitorDetailsSchema(
            id = dto.id,
            name = dto.name,
            host = dto.host,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            packetCount = dto.packetCount,
            timeoutSeconds = dto.timeoutSeconds,
            packetLossThreshold = dto.packetLossThreshold,
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
            effectiveIntegrations = dto.effectiveIntegrations.map { integration ->
                IntegrationDetailsSchema(
                    id = integration.id,
                    type = integration.type,
                    name = integration.name,
                    enabled = integration.enabled,
                    global = integration.global,
                    excludedEvents = integration.excludedEvents,
                )
            }.toSet(),
            statusPages = dto.statusPages,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IcmpMonitorStatsSchema(
    val id: Long,
    val metricsHistoryEnabled: Boolean,
    val uptimeHistory: HistoricalUptimeStatsSchema,
    val latencyStats: LatencyStatsSchema?,
    val packetLossStats: PacketLossStatsSchema?,
    val metricsLogs: List<IcmpMetricsLogSchema>,
) {
    companion object {
        fun fromDto(dto: IcmpMonitorStatsDto) = IcmpMonitorStatsSchema(
            id = dto.id,
            metricsHistoryEnabled = dto.metricsHistoryEnabled,
            uptimeHistory = HistoricalUptimeStatsSchema.fromDto(dto.uptimeHistory),
            latencyStats = dto.latencyStats?.let { LatencyStatsSchema.fromDto(it) },
            packetLossStats = dto.packetLossStats?.let { PacketLossStatsSchema.fromDto(it) },
            metricsLogs = dto.metricsLogs.map { IcmpMetricsLogSchema.fromDto(it) },
        )
    }
}
