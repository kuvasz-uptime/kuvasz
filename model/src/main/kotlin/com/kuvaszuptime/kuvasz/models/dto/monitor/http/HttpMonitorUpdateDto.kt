package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.validation.SupportedStatusCodes
import com.kuvaszuptime.kuvasz.validation.ValidHeaderNames
import com.kuvaszuptime.kuvasz.validation.WellFormedJsonString
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

@Introspected
data class HttpMonitorUpdateDto(
    @param:Schema(description = HttpMonitorDocs.NAME, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String?,

    @param:Schema(description = HttpMonitorDocs.URL, required = false, nullable = false)
    @get:Pattern(regexp = URI_REGEX, message = MonitorValidationMessages.URL_PATTERN)
    @get:NotNull(message = MonitorValidationMessages.URL_NOT_NULL)
    val url: String?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.SENSITIVE_URL, required = false, nullable = false)
    val sensitiveUrl: Boolean?,

    @get:Min(MIN_UPTIME_CHECK_INTERVAL, message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.UPTIME_CHECK_INTERVAL, required = false, nullable = false)
    val uptimeCheckInterval: Int?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.ENABLED, required = false, nullable = false)
    val enabled: Boolean?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.SSL_CHECK_ENABLED, required = false, nullable = false)
    val sslCheckEnabled: Boolean?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.REQUEST_METHOD, required = false, nullable = false)
    val requestMethod: HttpMethod?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.LATENCY_HISTORY_ENABLED, required = false, nullable = false)
    val latencyHistoryEnabled: Boolean?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.FORCE_NO_CACHE, required = false, nullable = false)
    val forceNoCache: Boolean?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.FOLLOW_REDIRECTS, required = false, nullable = false)
    val followRedirects: Boolean?,

    @get:NotNull
    @get:PositiveOrZero(message = MonitorValidationMessages.SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO)
    @param:Schema(description = HttpMonitorDocs.SSL_EXPIRY_THRESHOLD, required = false, nullable = false)
    val sslExpiryThreshold: Int?,

    @param:Schema(description = HttpMonitorDocs.INTEGRATIONS, required = false, nullable = true)
    val integrations: Set<IntegrationID>?,

    @get:SupportedStatusCodes
    @param:Schema(description = HttpMonitorDocs.EXPECTED_STATUS_CODES, required = false, nullable = true)
    val expectedStatusCodes: List<Int>?,

    @get:Positive(message = MonitorValidationMessages.RESPONSE_TIME_THRESHOLD_POSITIVE)
    @get:Max(
        Validation.MAX_RESPONSE_TIME_THRESHOLD_MILLIS,
        message = MonitorValidationMessages.RESPONSE_TIME_THRESHOLD_MAX
    )
    @param:Schema(description = HttpMonitorDocs.RESPONSE_TIME_THRESHOLD, required = false, nullable = true)
    val responseTimeThresholdMillis: Int?,

    @param:Schema(description = HttpMonitorDocs.EXPECTED_KEYWORD, required = false, nullable = true)
    val expectedKeyword: String?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.EXPECTED_KEYWORD_CASE_SENSITIVE, required = false, nullable = false)
    val expectedKeywordCaseSensitive: Boolean?,

    @get:NotNull
    @param:Schema(description = HttpMonitorDocs.EXPECTED_KEYWORD_NEGATED, required = false, nullable = false)
    val expectedKeywordNegated: Boolean?,

    @get:NotNull
    @get:ValidHeaderNames
    @param:Schema(description = HttpMonitorDocs.REQUEST_HEADERS, required = false, nullable = false)
    val requestHeaders: Map<String, String>?,

    @get:NotNull
    @get:ValidHeaderNames
    @param:Schema(description = HttpMonitorDocs.EXPECTED_HEADERS, required = false, nullable = false)
    val expectedHeaders: Map<String, String>?,

    @get:WellFormedJsonString
    @param:Schema(description = HttpMonitorDocs.REQUEST_BODY, required = false, nullable = true)
    val requestBody: String?,

    @get:NotNull
    @get:Positive(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE)
    @param:Schema(description = HttpMonitorDocs.FAILURE_COUNT_THRESHOLD, required = false, nullable = false)
    val failureCountThreshold: Long?,
)
