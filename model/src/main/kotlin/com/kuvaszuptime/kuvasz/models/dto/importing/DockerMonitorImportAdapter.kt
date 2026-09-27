package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.docker.DockerMonitorCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class DockerMonitorImportAdapter(dto: DockerMonitorExportDto) : DockerMonitorCreator {
    override val name: String = dto.name
    override val dockerHost: String = dto.dockerHost
    override val container: String = dto.container
    override val uptimeCheckInterval: Int = dto.uptimeCheckInterval
    override val timeoutMs: Int = dto.timeoutMs
    override val failureCountThreshold: Long = dto.failureCountThreshold
    override val enabled: Boolean = dto.enabled
    override val integrations: List<String> = dto.integrations.map { it.toString() }
    override val metricsHistoryEnabled: Boolean = dto.metricsHistoryEnabled
    override val category: String? = dto.category
    override val ignoreConnectivityCheck: Boolean = dto.ignoreConnectivityCheck
}
