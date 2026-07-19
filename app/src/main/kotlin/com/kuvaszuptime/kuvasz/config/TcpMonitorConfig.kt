package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorDefaults
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.bind.annotation.Bindable

@EachProperty(TcpMonitorConfig.CONFIG_PREFIX, list = true)
@Introspected
interface TcpMonitorConfig : TcpMonitorCreator, MonitorConfig {

    companion object {
        const val CONFIG_PREFIX = "tcp-monitors"
    }

    override val name: String
    override val host: String
    override val port: Int
    override val uptimeCheckInterval: Int

    @get:Bindable(defaultValue = TcpMonitorDefaults.TIMEOUT_MS.toString())
    override val timeoutMs: Int

    @get:Nullable
    override val latencyThresholdMs: Int?

    @get:Bindable(defaultValue = TcpMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString())
    override val failureCountThreshold: Long

    @get:Bindable(defaultValue = TcpMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean

    override val integrations: List<String>?

    @get:Bindable(defaultValue = TcpMonitorDefaults.METRICS_HISTORY_ENABLED.toString())
    override val metricsHistoryEnabled: Boolean
}
