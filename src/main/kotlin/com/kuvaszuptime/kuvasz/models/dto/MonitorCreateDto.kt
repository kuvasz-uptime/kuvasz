package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.MonitorCreatorLike
import io.micronaut.core.annotation.Introspected

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
    override val enabled: Boolean? = MonitorDefaults.MONITOR_ENABLED,
    override val sslCheckEnabled: Boolean = MonitorDefaults.SSL_CHECK_ENABLED,
    override val pagerdutyIntegrationKey: String? = null,
    override val requestMethod: HttpMethod = HttpMethod.valueOf(MonitorDefaults.REQUEST_METHOD),
    override val latencyHistoryEnabled: Boolean = MonitorDefaults.LATENCY_HISTORY_ENABLED,
    override val forceNoCache: Boolean = MonitorDefaults.FORCE_NO_CACHE,
    override val followRedirects: Boolean = MonitorDefaults.FOLLOW_REDIRECTS,
) : MonitorCreatorLike
