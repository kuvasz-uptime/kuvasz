package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class IcmpMonitorCreateDto(
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = IcmpMonitorDocs.HOST, required = true)
    override val host: String,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    override val uptimeCheckInterval: Int,
    @param:Schema(
        description = IcmpMonitorDocs.PACKET_COUNT,
        required = false,
        defaultValue = IcmpMonitorDefaults.PACKET_COUNT.toString()
    )
    override val packetCount: Int = IcmpMonitorDefaults.PACKET_COUNT,
    @param:Schema(
        description = IcmpMonitorDocs.TIMEOUT_SECONDS,
        required = false,
        defaultValue = IcmpMonitorDefaults.TIMEOUT_SECONDS.toString()
    )
    override val timeoutSeconds: Int = IcmpMonitorDefaults.TIMEOUT_SECONDS,
    @param:Schema(
        description = IcmpMonitorDocs.PACKET_LOSS_THRESHOLD,
        required = false,
        defaultValue = IcmpMonitorDefaults.PACKET_LOSS_THRESHOLD.toString()
    )
    override val packetLossThreshold: Int = IcmpMonitorDefaults.PACKET_LOSS_THRESHOLD,
    @param:Schema(
        description = MonitorDocs.FAILURE_COUNT_THRESHOLD,
        required = false,
        defaultValue = IcmpMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString()
    )
    override val failureCountThreshold: Long = IcmpMonitorDefaults.FAILURE_COUNT_THRESHOLD,
    @param:Schema(
        description = MonitorDocs.ENABLED,
        defaultValue = IcmpMonitorDefaults.MONITOR_ENABLED.toString()
    )
    override val enabled: Boolean = IcmpMonitorDefaults.MONITOR_ENABLED,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(
        description = IcmpMonitorDocs.METRICS_HISTORY_ENABLED,
        required = false,
        defaultValue = IcmpMonitorDefaults.METRICS_HISTORY_ENABLED.toString()
    )
    override val metricsHistoryEnabled: Boolean = IcmpMonitorDefaults.METRICS_HISTORY_ENABLED,
) : IcmpMonitorCreator
