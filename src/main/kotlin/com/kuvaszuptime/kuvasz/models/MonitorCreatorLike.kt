package com.kuvaszuptime.kuvasz.models

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

@Suppress("ComplexInterface")
interface MonitorCreatorLike {
    @get:NotBlank
    val name: String

    @get:NotNull
    @get:Pattern(regexp = Validation.URI_REGEX)
    val url: String

    @get:NotNull
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int
    val enabled: Boolean?
    val sslCheckEnabled: Boolean
    val pagerdutyIntegrationKey: String?
    val requestMethod: HttpMethod
    val latencyHistoryEnabled: Boolean
    val forceNoCache: Boolean
    val followRedirects: Boolean
}

fun MonitorCreatorLike.toMonitorRecord(): MonitorRecord =
    MonitorRecord()
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
