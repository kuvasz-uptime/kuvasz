package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected

@Introspected
data class IcmpMonitorExportDto(
    val name: String,
    val host: String,
    val uptimeCheckInterval: Int,
    val packetCount: Int,
    val timeoutSeconds: Int,
    val packetLossThreshold: Int,
    val failureCountThreshold: Long,
    val enabled: Boolean,
    val integrations: Set<IntegrationID>,
    val metricsHistoryEnabled: Boolean,
) {
    companion object {
        fun fromMonitorRecord(record: IcmpMonitorRecord): IcmpMonitorExportDto {
            return IcmpMonitorExportDto(
                name = record.name,
                host = record.host,
                uptimeCheckInterval = record.uptimeCheckInterval,
                packetCount = record.packetCount,
                timeoutSeconds = record.timeoutSeconds,
                packetLossThreshold = record.packetLossThreshold,
                failureCountThreshold = record.failureCountThreshold,
                enabled = record.enabled,
                integrations = record.integrations.toSet(),
                metricsHistoryEnabled = record.metricsHistoryEnabled,
            )
        }
    }
}
