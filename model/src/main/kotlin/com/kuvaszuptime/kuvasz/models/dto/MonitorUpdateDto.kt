package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.validation.SupportedStatusCodes
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
data class MonitorUpdateDto(
    @param:Schema(description = MonitorDocs.NAME, required = false, nullable = false)
    @get:NotBlank(message = ValidationMessages.NAME_NOT_BLANK)
    val name: String?,

    @param:Schema(description = MonitorDocs.URL, required = false, nullable = false)
    @get:Pattern(regexp = URI_REGEX, message = ValidationMessages.URL_PATTERN)
    @get:NotNull(message = ValidationMessages.URL_NOT_NULL)
    val url: String?,

    @get:Min(MIN_UPTIME_CHECK_INTERVAL, message = ValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    @get:NotNull
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = false, nullable = false)
    val uptimeCheckInterval: Int?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.ENABLED, required = false, nullable = false)
    val enabled: Boolean?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.SSL_CHECK_ENABLED, required = false, nullable = false)
    val sslCheckEnabled: Boolean?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.REQUEST_METHOD, required = false, nullable = false)
    val requestMethod: HttpMethod?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.LATENCY_HISTORY_ENABLED, required = false, nullable = false)
    val latencyHistoryEnabled: Boolean?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.FORCE_NO_CACHE, required = false, nullable = false)
    val forceNoCache: Boolean?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.FOLLOW_REDIRECTS, required = false, nullable = false)
    val followRedirects: Boolean?,

    @get:NotNull
    @get:PositiveOrZero(message = ValidationMessages.SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO)
    @param:Schema(description = MonitorDocs.SSL_EXPIRY_THRESHOLD, required = false, nullable = false)
    val sslExpiryThreshold: Int?,

    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false, nullable = true)
    val integrations: Set<IntegrationID>?,

    @get:SupportedStatusCodes
    @param:Schema(description = MonitorDocs.EXPECTED_STATUS_CODES, required = false, nullable = true)
    val expectedStatusCodes: List<Int>?,

    @get:Positive(message = ValidationMessages.RESPONSE_TIME_THRESHOLD_POSITIVE)
    @get:Max(Validation.MAX_RESPONSE_TIME_THRESHOLD_MILLIS, message = ValidationMessages.RESPONSE_TIME_THRESHOLD_MAX)
    @param:Schema(description = MonitorDocs.RESPONSE_TIME_THRESHOLD, required = false, nullable = true)
    val responseTimeThresholdMillis: Int?,

    @param:Schema(description = MonitorDocs.EXPECTED_KEYWORD, required = false, nullable = true)
    val expectedKeyword: String?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.EXPECTED_KEYWORD_CASE_SENSITIVE, required = false, nullable = false)
    val expectedKeywordCaseSensitive: Boolean?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.EXPECTED_KEYWORD_NEGATED, required = false, nullable = false)
    val expectedKeywordNegated: Boolean?,
)
