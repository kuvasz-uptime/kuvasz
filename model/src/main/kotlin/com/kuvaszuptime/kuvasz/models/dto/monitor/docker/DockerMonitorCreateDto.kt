package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.monitor.docker.DockerMonitorCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class DockerMonitorCreateDto(
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = DockerMonitorDocs.DOCKER_HOST, required = true)
    override val dockerHost: String,
    @param:Schema(description = DockerMonitorDocs.CONTAINER, required = true)
    override val container: String,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    override val uptimeCheckInterval: Int,
    @param:Schema(
        description = DockerMonitorDocs.TIMEOUT_MS,
        required = false,
        defaultValue = DockerMonitorDefaults.TIMEOUT_MS.toString()
    )
    override val timeoutMs: Int = DockerMonitorDefaults.TIMEOUT_MS,
    @param:Schema(
        description = MonitorDocs.FAILURE_COUNT_THRESHOLD,
        required = false,
        defaultValue = DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString()
    )
    override val failureCountThreshold: Long = DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD,
    @param:Schema(
        description = MonitorDocs.ENABLED,
        defaultValue = DockerMonitorDefaults.MONITOR_ENABLED.toString()
    )
    override val enabled: Boolean = DockerMonitorDefaults.MONITOR_ENABLED,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(
        description = DockerMonitorDocs.METRICS_HISTORY_ENABLED,
        required = false,
        defaultValue = DockerMonitorDefaults.METRICS_HISTORY_ENABLED.toString()
    )
    override val metricsHistoryEnabled: Boolean = DockerMonitorDefaults.METRICS_HISTORY_ENABLED,
    @param:Schema(description = MonitorDocs.CATEGORY, required = false, nullable = true)
    override val category: String? = null,
    @param:Schema(
        description = MonitorDocs.IGNORE_CONNECTIVITY_CHECK,
        required = false,
        defaultValue = DockerMonitorDefaults.IGNORE_CONNECTIVITY_CHECK.toString(),
    )
    override val ignoreConnectivityCheck: Boolean = DockerMonitorDefaults.IGNORE_CONNECTIVITY_CHECK,
) : DockerMonitorCreator
