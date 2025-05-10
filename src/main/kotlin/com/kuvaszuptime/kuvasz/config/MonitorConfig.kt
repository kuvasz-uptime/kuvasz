package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.MonitorCreatorLike
import com.kuvaszuptime.kuvasz.models.dto.MonitorDefaults
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

// TODO validate separately
@EachProperty(MonitorConfig.CONFIG_PREFIX, list = true)
@Introspected
@Suppress("ComplexInterface")
interface MonitorConfig : MonitorCreatorLike {

    companion object {
        const val CONFIG_PREFIX = "monitors"
    }

    override val name: String
    override val url: String
    override val uptimeCheckInterval: Int
    override val pagerdutyIntegrationKey: String?

    @get:Bindable(defaultValue = MonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean

    @get:Bindable(defaultValue = MonitorDefaults.SSL_CHECK_ENABLED.toString())
    override val sslCheckEnabled: Boolean

    @get:Bindable(defaultValue = MonitorDefaults.REQUEST_METHOD)
    override val requestMethod: HttpMethod

    @get:Bindable(defaultValue = MonitorDefaults.LATENCY_HISTORY_ENABLED.toString())
    override val latencyHistoryEnabled: Boolean

    @get:Bindable(defaultValue = MonitorDefaults.FORCE_NO_CACHE.toString())
    override val forceNoCache: Boolean

    @get:Bindable(defaultValue = MonitorDefaults.FOLLOW_REDIRECTS.toString())
    override val followRedirects: Boolean
}
