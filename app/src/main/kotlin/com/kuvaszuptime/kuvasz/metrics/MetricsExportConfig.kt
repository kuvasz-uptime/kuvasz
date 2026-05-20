package com.kuvaszuptime.kuvasz.metrics

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable

@ConfigurationProperties(MetricsExportConfig.CONFIG_PREFIX)
interface MetricsExportConfig {

    companion object {
        const val CONFIG_PREFIX = "metrics-exports"
    }

    @get:Bindable(defaultValue = "false")
    val httpUptimeStatus: Boolean

    @get:Bindable(defaultValue = "false")
    val httpLatestLatency: Boolean

    @get:Bindable(defaultValue = "false")
    val sslStatus: Boolean

    @get:Bindable(defaultValue = "false")
    val sslExpiry: Boolean

    @get:Bindable(defaultValue = "false")
    val pushUptimeStatus: Boolean

    @get:Bindable(defaultValue = "false")
    val icmpUptimeStatus: Boolean

    @get:Bindable(defaultValue = "false")
    val icmpLatestLatency: Boolean

    @get:Bindable(defaultValue = "false")
    val icmpLatestPacketLoss: Boolean
}
