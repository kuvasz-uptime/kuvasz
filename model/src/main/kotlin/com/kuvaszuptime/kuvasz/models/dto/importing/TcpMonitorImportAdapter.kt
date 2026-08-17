package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class TcpMonitorImportAdapter(dto: TcpMonitorExportDto) : TcpMonitorCreator {
    override val name: String = dto.name
    override val host: String = dto.host
    override val port: Int = dto.port
    override val uptimeCheckInterval: Int = dto.uptimeCheckInterval
    override val timeoutMs: Int = dto.timeoutMs
    override val latencyThresholdMs: Int? = dto.latencyThresholdMs
    override val failureCountThreshold: Long = dto.failureCountThreshold
    override val enabled: Boolean = dto.enabled
    override val integrations: List<String> = dto.integrations.map { it.toString() }
    override val metricsHistoryEnabled: Boolean = dto.metricsHistoryEnabled
    override val category: String? = dto.category
}
