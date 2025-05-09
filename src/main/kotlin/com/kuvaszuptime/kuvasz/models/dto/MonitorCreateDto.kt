package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
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
}

@Introspected
data class MonitorCreateDto(
    override val name: String,
    override val url: String,
    override val uptimeCheckInterval: Int,
    @Schema(required = false, defaultValue = "true")
    override val enabled: Boolean = MonitorDefaults.MONITOR_ENABLED,
    @Schema(required = false, defaultValue = "false")
    override val sslCheckEnabled: Boolean = MonitorDefaults.SSL_CHECK_ENABLED,
    @Schema(required = false)
    override val pagerdutyIntegrationKey: String? = null,
    @Schema(required = false, defaultValue = "GET")
    override val requestMethod: HttpMethod = HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD),
    @Schema(required = false, defaultValue = "true")
    override val latencyHistoryEnabled: Boolean = MonitorDefaults.LATENCY_HISTORY_ENABLED,
    @Schema(required = false, defaultValue = "true")
    override val forceNoCache: Boolean = MonitorDefaults.FORCE_NO_CACHE,
    @Schema(required = false, defaultValue = "true")
    override val followRedirects: Boolean = MonitorDefaults.FOLLOW_REDIRECTS,
) : MonitorCreatorLike
