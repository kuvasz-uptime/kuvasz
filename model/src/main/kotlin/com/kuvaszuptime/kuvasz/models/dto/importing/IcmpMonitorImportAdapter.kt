package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator

class IcmpMonitorImportAdapter(private val dto: IcmpMonitorExportDto) : IcmpMonitorCreator {
    override val name: String get() = dto.name
    override val host: String get() = dto.host
    override val uptimeCheckInterval: Int get() = dto.uptimeCheckInterval
    override val packetCount: Int get() = dto.packetCount
    override val timeoutSeconds: Int get() = dto.timeoutSeconds
    override val packetLossThreshold: Int get() = dto.packetLossThreshold
    override val failureCountThreshold: Long get() = dto.failureCountThreshold
    override val enabled: Boolean get() = dto.enabled
    override val integrations: List<String>? get() = dto.integrations.map { it.toString() }
    override val metricsHistoryEnabled: Boolean get() = dto.metricsHistoryEnabled
}
