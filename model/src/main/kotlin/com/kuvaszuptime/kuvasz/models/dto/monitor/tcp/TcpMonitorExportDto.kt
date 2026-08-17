package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected

@Introspected
data class TcpMonitorExportDto(
    val name: String,
    val host: String,
    val port: Int,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long,
    val enabled: Boolean,
    val integrations: Set<IntegrationID>,
    val metricsHistoryEnabled: Boolean,
    val category: String? = null,
) {
    companion object {
        fun fromMonitorRecord(record: TcpMonitorRecord): TcpMonitorExportDto {
            return TcpMonitorExportDto(
                name = record.name,
                host = record.host,
                port = record.port,
                uptimeCheckInterval = record.uptimeCheckInterval,
                timeoutMs = record.timeoutMs,
                latencyThresholdMs = record.latencyThresholdMs,
                failureCountThreshold = record.failureCountThreshold,
                enabled = record.enabled,
                integrations = record.integrations.toSet(),
                metricsHistoryEnabled = record.metricsHistoryEnabled,
                category = record.category,
            )
        }
    }
}
