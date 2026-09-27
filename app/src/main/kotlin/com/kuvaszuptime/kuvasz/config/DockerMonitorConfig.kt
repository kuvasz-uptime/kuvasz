package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDefaults
import com.kuvaszuptime.kuvasz.models.monitor.docker.DockerMonitorCreator
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable

@EachProperty(DockerMonitorConfig.CONFIG_PREFIX, list = true)
@Introspected
@Suppress("ComplexInterface")
interface DockerMonitorConfig : DockerMonitorCreator, MonitorConfig {

    companion object {
        const val CONFIG_PREFIX = "docker-monitors"
    }

    override val name: String
    override val dockerHost: String
    override val container: String
    override val uptimeCheckInterval: Int

    @get:Bindable(defaultValue = DockerMonitorDefaults.TIMEOUT_MS.toString())
    override val timeoutMs: Int

    @get:Bindable(defaultValue = DockerMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString())
    override val failureCountThreshold: Long

    @get:Bindable(defaultValue = DockerMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean

    override val integrations: List<String>?

    @get:Bindable(defaultValue = DockerMonitorDefaults.METRICS_HISTORY_ENABLED.toString())
    override val metricsHistoryEnabled: Boolean

    override val category: String?

    @get:Bindable(defaultValue = DockerMonitorDefaults.IGNORE_CONNECTIVITY_CHECK.toString())
    override val ignoreConnectivityCheck: Boolean
}
