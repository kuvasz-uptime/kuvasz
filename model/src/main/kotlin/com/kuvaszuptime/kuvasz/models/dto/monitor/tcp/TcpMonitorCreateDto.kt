package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class TcpMonitorCreateDto(
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = TcpMonitorDocs.HOST, required = true)
    override val host: String,
    @param:Schema(description = TcpMonitorDocs.PORT, required = true)
    override val port: Int,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    override val uptimeCheckInterval: Int,
    @param:Schema(
        description = TcpMonitorDocs.TIMEOUT_MS,
        required = false,
        defaultValue = TcpMonitorDefaults.TIMEOUT_MS.toString()
    )
    override val timeoutMs: Int = TcpMonitorDefaults.TIMEOUT_MS,
    @param:Schema(description = TcpMonitorDocs.LATENCY_THRESHOLD_MS, required = false, nullable = true)
    override val latencyThresholdMs: Int? = null,
    @param:Schema(
        description = MonitorDocs.FAILURE_COUNT_THRESHOLD,
        required = false,
        defaultValue = TcpMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString()
    )
    override val failureCountThreshold: Long = TcpMonitorDefaults.FAILURE_COUNT_THRESHOLD,
    @param:Schema(
        description = MonitorDocs.ENABLED,
        defaultValue = TcpMonitorDefaults.MONITOR_ENABLED.toString()
    )
    override val enabled: Boolean = TcpMonitorDefaults.MONITOR_ENABLED,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(
        description = TcpMonitorDocs.METRICS_HISTORY_ENABLED,
        required = false,
        defaultValue = TcpMonitorDefaults.METRICS_HISTORY_ENABLED.toString()
    )
    override val metricsHistoryEnabled: Boolean = TcpMonitorDefaults.METRICS_HISTORY_ENABLED,
) : TcpMonitorCreator
