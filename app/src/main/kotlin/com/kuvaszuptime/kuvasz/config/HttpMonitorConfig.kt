package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDefaults
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

@EachProperty(HttpMonitorConfig.CONFIG_PREFIX, list = true)
@Introspected
@Suppress("ComplexInterface")
interface HttpMonitorConfig : HttpMonitorCreator {

    companion object {
        const val CONFIG_PREFIX = "http-monitors"
    }

    override val name: String
    override val url: String
    override val uptimeCheckInterval: Int

    @get:Bindable(defaultValue = HttpMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean

    @get:Bindable(defaultValue = HttpMonitorDefaults.SSL_CHECK_ENABLED.toString())
    override val sslCheckEnabled: Boolean

    @get:Bindable(defaultValue = HttpMonitorDefaults.REQUEST_METHOD)
    override val requestMethod: HttpMethod

    @get:Bindable(defaultValue = HttpMonitorDefaults.LATENCY_HISTORY_ENABLED.toString())
    override val latencyHistoryEnabled: Boolean

    @get:Bindable(defaultValue = HttpMonitorDefaults.FORCE_NO_CACHE.toString())
    override val forceNoCache: Boolean

    @get:Bindable(defaultValue = HttpMonitorDefaults.FOLLOW_REDIRECTS.toString())
    override val followRedirects: Boolean

    @get:Bindable(defaultValue = HttpMonitorDefaults.SSL_EXPIRY_THRESHOLD_DAYS.toString())
    override val sslExpiryThreshold: Int

    override val integrations: List<String>?
    override val expectedStatusCodes: List<Int>?
    override val responseTimeThresholdMillis: Int?
    override val expectedKeyword: String?

    @get:Bindable(defaultValue = HttpMonitorDefaults.EXPECTED_KEYWORD_CASE_SENSITIVE.toString())
    override val expectedKeywordCaseSensitive: Boolean

    @get:Bindable(defaultValue = HttpMonitorDefaults.EXPECTED_KEYWORD_NEGATED.toString())
    override val expectedKeywordNegated: Boolean

    override val requestHeaders: Map<String, String>?
    override val expectedHeaders: Map<String, String>?
    override val requestBody: String?
}
