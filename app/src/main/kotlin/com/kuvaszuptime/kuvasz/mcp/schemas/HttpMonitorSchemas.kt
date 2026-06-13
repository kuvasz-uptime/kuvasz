package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDefaults
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import java.time.Duration
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
    val period: String,
    val latencyHistoryEnabled: Boolean,
    val latencyStats: LatencyStatsSchema?,
    val uptimeHistory: HistoricalUptimeStatsSchema,
    val latencyLogs: List<LatencyLogSchema>,
) {
    companion object {
        fun fromDto(dto: HttpMonitorStatsDto, period: Duration) = HttpMonitorStatsSchema(
            id = dto.id,
            period = period.toString(),
            latencyHistoryEnabled = dto.latencyHistoryEnabled,
            latencyStats = dto.latencyStats?.let { LatencyStatsSchema.fromDto(it) },
            uptimeHistory = HistoricalUptimeStatsSchema.fromDto(dto.uptimeHistory),
            latencyLogs = dto.latencyLogs.map { LatencyLogSchema.fromDto(it) },
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HttpMonitorSummarySchema(
    val id: Long,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val sslStatus: SslStatus?,
    val sslStatusStartedAt: OffsetDateTime?,
    val uptimeError: String?,
    val sslError: String?,
    val sslValidUntil: OffsetDateTime?,
) {
    companion object {
        fun fromDto(dto: HttpMonitorDetailsDto) =
            HttpMonitorSummarySchema(
                id = dto.id,
                name = dto.name,
                url = dto.url.toString(),
                enabled = dto.enabled,
                sslCheckEnabled = dto.sslCheckEnabled,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt,
                uptimeStatus = dto.uptimeStatus,
                uptimeStatusStartedAt = dto.uptimeStatusStartedAt,
                sslStatus = dto.sslStatus,
                sslStatusStartedAt = dto.sslStatusStartedAt,
                uptimeError = dto.uptimeError,
                sslError = dto.sslError,
                sslValidUntil = dto.sslValidUntil,
            )
    }
}

@Introspected
@JsonSchema
data class HttpMonitorCreatorSchema(
    @get:NotBlank
    val name: String,
    @get:Pattern(regexp = Validation.URI_REGEX)
    val url: String,
    val sensitiveUrl: Boolean?,
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int,
    val enabled: Boolean?,
    val sslCheckEnabled: Boolean?,
    val requestMethod: HttpMethod?,
    val latencyHistoryEnabled: Boolean?,
    val forceNoCache: Boolean?,
    val followRedirects: Boolean?,
    @get:PositiveOrZero
    val sslExpiryThreshold: Int?,
    val integrations: List<String>?,
    val expectedStatusCodes: List<Int>?,
    @get:Positive
    @get:Max(Validation.MAX_RESPONSE_TIME_THRESHOLD_MILLIS)
    val responseTimeThresholdMillis: Int? = null,
    val expectedKeyword: String? = null,
    val expectedKeywordCaseSensitive: Boolean?,
    val expectedKeywordNegated: Boolean?,
    val requestHeaders: Map<String, String>?,
    val expectedHeaders: Map<String, String>?,
    val requestBody: String? = null,
    @get:Positive
    val failureCountThreshold: Long?,
) {

    fun toDto() = HttpMonitorCreateDto(
        name = name,
        url = url,
        sensitiveUrl = sensitiveUrl ?: HttpMonitorDefaults.SENSITIVE_URL,
        uptimeCheckInterval = uptimeCheckInterval,
        enabled = enabled ?: HttpMonitorDefaults.MONITOR_ENABLED,
        sslCheckEnabled = sslCheckEnabled ?: HttpMonitorDefaults.SSL_CHECK_ENABLED,
        requestMethod = requestMethod ?: HttpMethod.valueOf(HttpMonitorDefaults.REQUEST_METHOD),
        latencyHistoryEnabled = latencyHistoryEnabled ?: HttpMonitorDefaults.LATENCY_HISTORY_ENABLED,
        forceNoCache = forceNoCache ?: HttpMonitorDefaults.FORCE_NO_CACHE,
        followRedirects = followRedirects ?: HttpMonitorDefaults.FOLLOW_REDIRECTS,
        sslExpiryThreshold = sslExpiryThreshold ?: HttpMonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS,
        integrations = integrations,
        expectedStatusCodes = expectedStatusCodes.orEmpty(),
        responseTimeThresholdMillis = responseTimeThresholdMillis,
        expectedKeyword = expectedKeyword,
        expectedKeywordCaseSensitive = expectedKeywordCaseSensitive
            ?: HttpMonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE,
        expectedKeywordNegated = expectedKeywordNegated ?: HttpMonitorDefaults.EXPECTED_KEYWORD_NEGATED,
        requestHeaders = requestHeaders.orEmpty(),
        expectedHeaders = expectedHeaders.orEmpty(),
        requestBody = requestBody,
        failureCountThreshold = failureCountThreshold ?: HttpMonitorDefaults.FAILURE_COUNT_THRESHOLD,
    )
}
