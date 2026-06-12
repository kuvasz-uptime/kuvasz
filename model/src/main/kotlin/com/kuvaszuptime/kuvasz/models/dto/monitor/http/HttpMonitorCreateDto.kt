package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class HttpMonitorCreateDto(
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = HttpMonitorDocs.URL, required = true)
    override val url: String,
    @param:Schema(
        description = HttpMonitorDocs.SENSITIVE_URL,
        required = false,
        defaultValue = HttpMonitorDefaults.SENSITIVE_URL.toString()
    )
    override val sensitiveUrl: Boolean = HttpMonitorDefaults.SENSITIVE_URL,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    override val uptimeCheckInterval: Int,
    @param:Schema(description = MonitorDocs.ENABLED, defaultValue = HttpMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean = HttpMonitorDefaults.MONITOR_ENABLED,
    @param:Schema(
        description = HttpMonitorDocs.SSL_CHECK_ENABLED,
        required = false,
        defaultValue = HttpMonitorDefaults.SSL_CHECK_ENABLED.toString()
    )
    override val sslCheckEnabled: Boolean = HttpMonitorDefaults.SSL_CHECK_ENABLED,
    @param:Schema(
        description = HttpMonitorDocs.REQUEST_METHOD,
        required = false,
        defaultValue = HttpMonitorDefaults.REQUEST_METHOD
    )
    override val requestMethod: HttpMethod = HttpMethod.valueOf(HttpMonitorDefaults.REQUEST_METHOD),
    @param:Schema(
        description = HttpMonitorDocs.LATENCY_HISTORY_ENABLED,
        required = false,
        defaultValue = HttpMonitorDefaults.LATENCY_HISTORY_ENABLED.toString()
    )
    override val latencyHistoryEnabled: Boolean = HttpMonitorDefaults.LATENCY_HISTORY_ENABLED,
    @param:Schema(
        description = HttpMonitorDocs.FORCE_NO_CACHE,
        required = false,
        defaultValue = HttpMonitorDefaults.FORCE_NO_CACHE.toString()
    )
    override val forceNoCache: Boolean = HttpMonitorDefaults.FORCE_NO_CACHE,
    @param:Schema(
        description = HttpMonitorDocs.FOLLOW_REDIRECTS,
        required = false,
        defaultValue = HttpMonitorDefaults.FOLLOW_REDIRECTS.toString()
    )
    override val followRedirects: Boolean = HttpMonitorDefaults.FOLLOW_REDIRECTS,
    @param:Schema(
        description = HttpMonitorDocs.SSL_EXPIRY_THRESHOLD,
        required = false,
        defaultValue = HttpMonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS.toString()
    )
    override val sslExpiryThreshold: Int = HttpMonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(description = HttpMonitorDocs.EXPECTED_STATUS_CODES, required = false)
    override val expectedStatusCodes: List<Int>? = emptyList(),
    @param:Schema(description = HttpMonitorDocs.RESPONSE_TIME_THRESHOLD, required = false)
    override val responseTimeThresholdMillis: Int? = null,
    @param:Schema(description = HttpMonitorDocs.EXPECTED_KEYWORD, required = false)
    override val expectedKeyword: String? = null,
    @param:Schema(
        description = HttpMonitorDocs.EXPECTED_KEYWORD_CASE_SENSITIVE,
        required = false,
        defaultValue = HttpMonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE.toString(),
    )
    override val expectedKeywordCaseSensitive: Boolean = HttpMonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE,
    @param:Schema(
        description = HttpMonitorDocs.EXPECTED_KEYWORD_NEGATED,
        required = false,
        defaultValue = HttpMonitorDefaults.EXPECTED_KEYWORD_NEGATED.toString(),
    )
    override val expectedKeywordNegated: Boolean = HttpMonitorDefaults.EXPECTED_KEYWORD_NEGATED,

    @param:Schema(description = HttpMonitorDocs.REQUEST_HEADERS, required = false)
    override val requestHeaders: Map<String, String> = emptyMap(),

    @param:Schema(description = HttpMonitorDocs.EXPECTED_HEADERS, required = false)
    override val expectedHeaders: Map<String, String> = emptyMap(),

    @param:Schema(description = HttpMonitorDocs.REQUEST_BODY, required = false, nullable = true)
    override val requestBody: String? = null,

    @param:Schema(
        description = MonitorDocs.FAILURE_COUNT_THRESHOLD,
        required = false,
        defaultValue = HttpMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString()
    )
    override val failureCountThreshold: Long = HttpMonitorDefaults.FAILURE_COUNT_THRESHOLD,
) : HttpMonitorCreator
