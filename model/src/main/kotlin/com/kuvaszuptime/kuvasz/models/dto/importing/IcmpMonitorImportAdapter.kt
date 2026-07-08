package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class IcmpMonitorImportAdapter(dto: IcmpMonitorExportDto) : IcmpMonitorCreator {
    override val name: String = dto.name
    override val host: String = dto.host
    override val uptimeCheckInterval: Int = dto.uptimeCheckInterval
    override val packetCount: Int = dto.packetCount
    override val timeoutSeconds: Int = dto.timeoutSeconds
    override val packetLossThreshold: Int = dto.packetLossThreshold
    override val failureCountThreshold: Long = dto.failureCountThreshold
    override val enabled: Boolean = dto.enabled
    override val integrations: List<String> = dto.integrations.map { it.toString() }
    override val metricsHistoryEnabled: Boolean = dto.metricsHistoryEnabled
}
