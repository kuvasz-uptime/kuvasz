package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.MonitorCreatorLike
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

object MonitorDefaults {
    const val MONITOR_ENABLED = true
    const val SSL_CHECK_ENABLED = false
    const val REQUEST_METHOD = "GET"
    const val LATENCY_HISTORY_ENABLED = true
    const val FORCE_NO_CACHE = true
    const val FOLLOW_REDIRECTS = true
    const val SSL_EXPIRY_THRESHOLD_DAYS = 30
    const val EXPECTED_KEYWORD_CASE_SENSITIVE = false
    const val EXPECTED_KEYWORD_NEGATED = false
}

@Introspected
data class MonitorCreateDto(
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = MonitorDocs.URL, required = true)
    override val url: String,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    override val uptimeCheckInterval: Int,
    @param:Schema(description = MonitorDocs.ENABLED, defaultValue = MonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean = MonitorDefaults.MONITOR_ENABLED,
    @param:Schema(
        description = MonitorDocs.SSL_CHECK_ENABLED,
        required = false,
        defaultValue = MonitorDefaults.SSL_CHECK_ENABLED.toString()
    )
    override val sslCheckEnabled: Boolean = MonitorDefaults.SSL_CHECK_ENABLED,
    @param:Schema(
        description = MonitorDocs.REQUEST_METHOD,
        required = false,
        defaultValue = MonitorDefaults.REQUEST_METHOD
    )
    override val requestMethod: HttpMethod = HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD),
    @param:Schema(
        description = MonitorDocs.LATENCY_HISTORY_ENABLED,
        required = false,
        defaultValue = MonitorDefaults.LATENCY_HISTORY_ENABLED.toString()
    )
    override val latencyHistoryEnabled: Boolean = MonitorDefaults.LATENCY_HISTORY_ENABLED,
    @param:Schema(
        description = MonitorDocs.FORCE_NO_CACHE,
        required = false,
        defaultValue = MonitorDefaults.FORCE_NO_CACHE.toString()
    )
    override val forceNoCache: Boolean = MonitorDefaults.FORCE_NO_CACHE,
    @param:Schema(
        description = MonitorDocs.FOLLOW_REDIRECTS,
        required = false,
        defaultValue = MonitorDefaults.FOLLOW_REDIRECTS.toString()
    )
    override val followRedirects: Boolean = MonitorDefaults.FOLLOW_REDIRECTS,
    @param:Schema(
        description = MonitorDocs.SSL_EXPIRY_THRESHOLD,
        required = false,
        defaultValue = MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS.toString()
    )
    override val sslExpiryThreshold: Int = MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(description = MonitorDocs.EXPECTED_STATUS_CODES, required = false)
    override val expectedStatusCodes: List<Int>? = emptyList(),
    @param:Schema(description = MonitorDocs.RESPONSE_TIME_THRESHOLD, required = false)
    override val responseTimeThresholdMillis: Int? = null,
    @param:Schema(description = MonitorDocs.EXPECTED_KEYWORD, required = false)
    override val expectedKeyword: String? = null,
    @param:Schema(
        description = MonitorDocs.EXPECTED_KEYWORD_CASE_SENSITIVE,
        required = false,
        defaultValue = MonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE.toString(),
    )
    override val expectedKeywordCaseSensitive: Boolean = MonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE,
    @param:Schema(
        description = MonitorDocs.EXPECTED_KEYWORD_NEGATED,
        required = false,
        defaultValue = MonitorDefaults.EXPECTED_KEYWORD_NEGATED.toString(),
    )
    override val expectedKeywordNegated: Boolean = MonitorDefaults.EXPECTED_KEYWORD_NEGATED,
) : MonitorCreatorLike
