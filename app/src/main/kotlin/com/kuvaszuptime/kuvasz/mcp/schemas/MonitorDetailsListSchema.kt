package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import java.time.OffsetDateTime

@JsonSchema
@Introspected
data class HttpMonitorListSchema(
    val monitors: List<HttpMonitorSummarySchema>,
)

@JsonSchema
@Introspected
data class PushMonitorListSchema(
    val monitors: List<PushMonitorSummarySchema>,
)

@JsonSchema
@Introspected
data class IcmpMonitorListSchema(
    val monitors: List<IcmpMonitorSummarySchema>,
)

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttpMonitorDetailsSchema(
    val id: Long,
    val name: String,
    val url: String,
    val sensitiveUrl: Boolean,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val lastUptimeCheck: OffsetDateTime?,
    val nextUptimeCheck: OffsetDateTime?,
    val sslStatus: SslStatus?,
    val sslStatusStartedAt: OffsetDateTime?,
    val lastSSLCheck: OffsetDateTime?,
    val nextSSLCheck: OffsetDateTime?,
    val uptimeError: String?,
    val sslError: String?,
    val requestMethod: HttpMethod,
    val latencyHistoryEnabled: Boolean,
    val forceNoCache: Boolean,
    val followRedirects: Boolean,
    val sslExpiryThreshold: Int,
    val failureCountThreshold: Long,
    val sslValidUntil: OffsetDateTime?,
    val integrations: Set<String>,
    val expectedStatusCodes: Set<Int>,
    val responseTimeThresholdMillis: Int?,
    val expectedKeyword: String?,
    val expectedKeywordCaseSensitive: Boolean,
    val expectedKeywordNegated: Boolean,
    val requestHeaders: Map<String, String>,
    val expectedHeaders: Map<String, String>,
    val requestBody: String?,
    val statusPages: Set<String>,
) {
    companion object {
        fun fromDto(dto: HttpMonitorDetailsDto) =
            HttpMonitorDetailsSchema(
                id = dto.id,
                name = dto.name,
                url = dto.url.toString(),
                sensitiveUrl = dto.sensitiveUrl,
                uptimeCheckInterval = dto.uptimeCheckInterval,
                enabled = dto.enabled,
                sslCheckEnabled = dto.sslCheckEnabled,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt,
                uptimeStatus = dto.uptimeStatus,
                uptimeStatusStartedAt = dto.uptimeStatusStartedAt,
                lastUptimeCheck = dto.lastUptimeCheck,
                nextUptimeCheck = dto.nextUptimeCheck,
                sslStatus = dto.sslStatus,
                sslStatusStartedAt = dto.sslStatusStartedAt,
                lastSSLCheck = dto.lastSSLCheck,
                nextSSLCheck = dto.nextSSLCheck,
                uptimeError = dto.uptimeError,
                sslError = dto.sslError,
                requestMethod = dto.requestMethod,
                latencyHistoryEnabled = dto.latencyHistoryEnabled,
                forceNoCache = dto.forceNoCache,
                followRedirects = dto.followRedirects,
                sslExpiryThreshold = dto.sslExpiryThreshold,
                failureCountThreshold = dto.failureCountThreshold,
                sslValidUntil = dto.sslValidUntil,
                integrations = dto.integrations.map { it.toString() }.toSet(),
                expectedStatusCodes = dto.expectedStatusCodes,
                responseTimeThresholdMillis = dto.responseTimeThresholdMillis,
                expectedKeyword = dto.expectedKeyword,
                expectedKeywordCaseSensitive = dto.expectedKeywordCaseSensitive,
                expectedKeywordNegated = dto.expectedKeywordNegated,
                requestHeaders = dto.requestHeaders,
                expectedHeaders = dto.expectedHeaders,
                requestBody = dto.requestBody,
                statusPages = dto.statusPages,
            )
    }
}
