package com.kuvaszuptime.kuvasz.mcp.models

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import java.time.OffsetDateTime

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttpMonitorSchema(
    val id: Long,
    val name: String,
    val url: String,
    val sensitiveUrl: Boolean,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val requestMethod: HttpMethod,
    val latencyHistoryEnabled: Boolean,
    val forceNoCache: Boolean,
    val followRedirects: Boolean,
    val sslExpiryThreshold: Int,
    val failureCountThreshold: Long,
    val integrations: Set<String>,
    val expectedStatusCodes: Set<Int>,
    val responseTimeThresholdMillis: Int?,
    val expectedKeyword: String?,
    val expectedKeywordCaseSensitive: Boolean,
    val expectedKeywordNegated: Boolean,
    val requestHeaders: Map<String, String>,
    val expectedHeaders: Map<String, String>,
    val requestBody: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?,
) {
    companion object {
        fun fromDto(dto: HttpMonitorDto) = HttpMonitorSchema(
            id = dto.id,
            name = dto.name,
            url = dto.url,
            sensitiveUrl = dto.sensitiveUrl,
            uptimeCheckInterval = dto.uptimeCheckInterval,
            enabled = dto.enabled,
            sslCheckEnabled = dto.sslCheckEnabled,
            requestMethod = dto.requestMethod,
            latencyHistoryEnabled = dto.latencyHistoryEnabled,
            forceNoCache = dto.forceNoCache,
            followRedirects = dto.followRedirects,
            sslExpiryThreshold = dto.sslExpiryThreshold,
            failureCountThreshold = dto.failureCountThreshold,
            integrations = dto.integrations.map { it.toString() }.toSet(),
            expectedStatusCodes = dto.expectedStatusCodes,
            responseTimeThresholdMillis = dto.responseTimeThresholdMillis,
            expectedKeyword = dto.expectedKeyword,
            expectedKeywordCaseSensitive = dto.expectedKeywordCaseSensitive,
            expectedKeywordNegated = dto.expectedKeywordNegated,
            requestHeaders = dto.requestHeaders,
            expectedHeaders = dto.expectedHeaders,
            requestBody = dto.requestBody,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttpMonitorStatsSchema(
    val id: Long,
    val latencyHistoryEnabled: Boolean,
    val latencyStats: LatencyStatsSchema?,
    val uptimeHistory: HistoricalUptimeStatsSchema,
    val latencyLogs: List<LatencyLogSchema>,
) {
    companion object {
        fun fromDto(dto: HttpMonitorStatsDto) = HttpMonitorStatsSchema(
            id = dto.id,
            latencyHistoryEnabled = dto.latencyHistoryEnabled,
            latencyStats = dto.latencyStats?.let { LatencyStatsSchema.fromDto(it) },
            uptimeHistory = HistoricalUptimeStatsSchema.fromDto(dto.uptimeHistory),
            latencyLogs = dto.latencyLogs.map { LatencyLogSchema.fromDto(it) },
        )
    }
}
