package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDefaults
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

@EachProperty(IcmpMonitorConfig.CONFIG_PREFIX, list = true)
@Introspected
interface IcmpMonitorConfig : IcmpMonitorCreator {

    companion object {
        const val CONFIG_PREFIX = "icmp-monitors"
    }

    override val name: String
    override val host: String
    override val uptimeCheckInterval: Int

    @get:Bindable(defaultValue = IcmpMonitorDefaults.PACKET_COUNT.toString())
    override val packetCount: Int

    @get:Bindable(defaultValue = IcmpMonitorDefaults.TIMEOUT_SECONDS.toString())
    override val timeoutSeconds: Int

    @get:Bindable(defaultValue = IcmpMonitorDefaults.PACKET_LOSS_THRESHOLD.toString())
    override val packetLossThreshold: Int

    @get:Bindable(defaultValue = IcmpMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString())
    override val failureCountThreshold: Long

    @get:Bindable(defaultValue = IcmpMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean

    override val integrations: List<String>?

    @get:Bindable(defaultValue = IcmpMonitorDefaults.METRICS_HISTORY_ENABLED.toString())
    override val metricsHistoryEnabled: Boolean
}
