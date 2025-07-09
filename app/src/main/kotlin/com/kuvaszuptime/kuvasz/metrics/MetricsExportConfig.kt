package com.kuvaszuptime.kuvasz.metrics

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable

@ConfigurationProperties(MetricsExportConfig.CONFIG_PREFIX)
interface MetricsExportConfig {

    companion object {
        const val CONFIG_PREFIX = "metrics-exports"
    }

    @get:Bindable(defaultValue = "false")
    val uptimeStatus: Boolean

    @get:Bindable(defaultValue = "false")
    val latestLatency: Boolean

    @get:Bindable(defaultValue = "false")
    val sslStatus: Boolean

    @get:Bindable(defaultValue = "false")
    val sslExpiry: Boolean
}
