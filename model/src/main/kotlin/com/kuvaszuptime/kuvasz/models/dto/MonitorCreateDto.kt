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
}

@Introspected
data class MonitorCreateDto(
    @Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @Schema(description = MonitorDocs.URL, required = true)
    override val url: String,
    @Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    override val uptimeCheckInterval: Int,
    @Schema(description = MonitorDocs.ENABLED, defaultValue = "true")
    override val enabled: Boolean = MonitorDefaults.MONITOR_ENABLED,
    @Schema(description = MonitorDocs.SSL_CHECK_ENABLED, required = false, defaultValue = "false")
    override val sslCheckEnabled: Boolean = MonitorDefaults.SSL_CHECK_ENABLED,
    @Schema(description = MonitorDocs.REQUEST_METHOD, required = false, defaultValue = "GET")
    override val requestMethod: HttpMethod = HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD),
    @Schema(description = MonitorDocs.LATENCY_HISTORY_ENABLED, required = false, defaultValue = "true")
    override val latencyHistoryEnabled: Boolean = MonitorDefaults.LATENCY_HISTORY_ENABLED,
    @Schema(description = MonitorDocs.FORCE_NO_CACHE, required = false, defaultValue = "true")
    override val forceNoCache: Boolean = MonitorDefaults.FORCE_NO_CACHE,
    @Schema(description = MonitorDocs.FOLLOW_REDIRECTS, required = false, defaultValue = "true")
    override val followRedirects: Boolean = MonitorDefaults.FOLLOW_REDIRECTS,
    @Schema(description = MonitorDocs.SSL_EXPIRY_THRESHOLD, required = false, defaultValue = "30")
    override val sslExpiryThreshold: Int = MonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS,
    @Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
) : MonitorCreatorLike
