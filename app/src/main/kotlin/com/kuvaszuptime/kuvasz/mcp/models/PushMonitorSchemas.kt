package com.kuvaszuptime.kuvasz.mcp.models

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorStatsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import java.time.OffsetDateTime

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PushMonitorSchema(
    val id: Long,
    val name: String,
    val heartbeatInterval: Long,
    val gracePeriod: Long,
    val lastHeartbeat: OffsetDateTime?,
    val clientSecret: String,
    val enabled: Boolean,
    val integrations: Set<String>,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val failureCountThreshold: Long,
) {
    companion object {
        fun fromDto(dto: PushMonitorDto) = PushMonitorSchema(
            id = dto.id,
            name = dto.name,
            heartbeatInterval = dto.heartbeatInterval,
            gracePeriod = dto.gracePeriod,
            lastHeartbeat = dto.lastHeartbeat,
            clientSecret = dto.clientSecret,
            enabled = dto.enabled,
            integrations = dto.integrations.map { it.toString() }.toSet(),
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            failureCountThreshold = dto.failureCountThreshold,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PushMonitorDetailsSchema(
    val id: Long,
    val name: String,
    val heartbeatInterval: Long,
    val gracePeriod: Long,
    val clientSecret: String,
    val enabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val lastUptimeCheck: OffsetDateTime?,
    val lastHeartbeat: OffsetDateTime?,
    val nextExpectedHeartbeat: OffsetDateTime?,
    val uptimeError: String?,
    val integrations: Set<String>,
    val effectiveIntegrations: Set<IntegrationDetailsSchema>,
    val statusPages: Set<String>,
    val failureCountThreshold: Long,
) {
    companion object {
        fun fromDto(dto: PushMonitorDetailsDto) = PushMonitorDetailsSchema(
            id = dto.id,
            name = dto.name,
            heartbeatInterval = dto.heartbeatInterval,
            gracePeriod = dto.gracePeriod,
            clientSecret = dto.clientSecret,
            enabled = dto.enabled,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
            uptimeStatus = dto.uptimeStatus,
            uptimeStatusStartedAt = dto.uptimeStatusStartedAt,
            lastUptimeCheck = dto.lastUptimeCheck,
            lastHeartbeat = dto.lastHeartbeat,
            nextExpectedHeartbeat = dto.nextExpectedHeartbeat,
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
            failureCountThreshold = dto.failureCountThreshold,
        )
    }
}

@JsonSchema
@Introspected
data class PushMonitorStatsSchema(
    val id: Long,
    val uptimeHistory: HistoricalUptimeStatsSchema,
) {
    companion object {
        fun fromDto(dto: PushMonitorStatsDto) = PushMonitorStatsSchema(
            id = dto.id,
            uptimeHistory = HistoricalUptimeStatsSchema.fromDto(dto.uptimeHistory),
        )
    }
}
