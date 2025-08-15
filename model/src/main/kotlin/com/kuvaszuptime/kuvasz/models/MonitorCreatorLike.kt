package com.kuvaszuptime.kuvasz.models

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.validation.SupportedStatusCodes
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero

@Suppress("ComplexInterface")
interface MonitorCreatorLike {
    @get:NotBlank(message = ValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotNull(message = ValidationMessages.URL_NOT_NULL)
    @get:Pattern(regexp = Validation.URI_REGEX, message = ValidationMessages.URL_PATTERN)
    val url: String

    @get:NotNull(message = ValidationMessages.UPTIME_CHECK_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL, message = ValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    val uptimeCheckInterval: Int
    val enabled: Boolean
    val sslCheckEnabled: Boolean
    val requestMethod: HttpMethod
    val latencyHistoryEnabled: Boolean
    val forceNoCache: Boolean
    val followRedirects: Boolean

    @get:NotNull(message = ValidationMessages.SSL_EXPIRY_THRESHOLD_NOT_NULL)
    @get:PositiveOrZero(message = ValidationMessages.SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO)
    val sslExpiryThreshold: Int

    val integrations: List<String>?

    @get:SupportedStatusCodes
    val expectedStatusCodes: List<Int>?

    @get:Positive(message = ValidationMessages.RESPONSE_TIME_THRESHOLD_POSITIVE)
    @get:Max(
        Validation.MAX_RESPONSE_TIME_THRESHOLD_MILLIS,
        message = ValidationMessages.RESPONSE_TIME_THRESHOLD_MAX,
    )
    val responseTimeThresholdMillis: Int?
    val expectedKeyword: String?
    val expectedKeywordCaseSensitive: Boolean
    val expectedKeywordNegated: Boolean
}

fun MonitorCreatorLike.toMonitorRecord(validatedIntegrations: Set<IntegrationID>): MonitorRecord =
    MonitorRecord()
        .setName(name)
        .setUrl(url)
        .setEnabled(enabled)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setSslCheckEnabled(sslCheckEnabled)
        .setRequestMethod(requestMethod)
        .setLatencyHistoryEnabled(latencyHistoryEnabled)
        .setForceNoCache(forceNoCache)
        .setFollowRedirects(followRedirects)
        .setSslExpiryThreshold(sslExpiryThreshold)
        .setIntegrations(validatedIntegrations.toTypedArray())
        .setExpectedStatusCodes(expectedStatusCodes?.toSet()?.toTypedArray().orEmpty())
        .setResponseTimeThresholdMillis(responseTimeThresholdMillis)
        .setExpectedKeyword(expectedKeyword)
        .setExpectedKeywordCaseSensitive(expectedKeywordCaseSensitive)
        .setExpectedKeywordNegated(expectedKeywordNegated)
