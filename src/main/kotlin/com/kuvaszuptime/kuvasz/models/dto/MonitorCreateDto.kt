package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

@Introspected
data class MonitorCreateDto(
    @get:NotBlank
    val name: String,
    @get:NotNull
    @get:Pattern(regexp = URI_REGEX)
    val url: String,
    @get:NotNull
    @get:Min(MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int,
    val enabled: Boolean? = true,
    val sslCheckEnabled: Boolean? = false,
    val pagerdutyIntegrationKey: String? = null,
    val requestMethod: HttpMethod? = HttpMethod.GET,
    val latencyHistoryEnabled: Boolean? = true,
    val forceNoCache: Boolean? = true,
    val followRedirects: Boolean? = true,
) {
    fun toMonitorPojo(): MonitorPojo = MonitorPojo()
        .setName(name)
        .setUrl(url)
        .setEnabled(enabled)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setSslCheckEnabled(sslCheckEnabled)
        .setPagerdutyIntegrationKey(pagerdutyIntegrationKey)
        .setRequestMethod(requestMethod)
        .setLatencyHistoryEnabled(latencyHistoryEnabled)
        .setForceNoCache(forceNoCache)
        .setFollowRedirects(followRedirects)
}
