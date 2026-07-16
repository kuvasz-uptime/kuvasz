package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class TcpMonitorDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @param:Schema(description = TcpMonitorDocs.HOST, required = true)
    val host: String,
    @param:Schema(description = TcpMonitorDocs.PORT, required = true)
    val port: Int,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @param:Schema(description = TcpMonitorDocs.TIMEOUT_MS, required = true)
    val timeoutMs: Int,
    @param:Schema(description = TcpMonitorDocs.LATENCY_THRESHOLD_MS, required = true, nullable = true)
    val latencyThresholdMs: Int?,
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
    @param:Schema(description = TcpMonitorDocs.METRICS_HISTORY_ENABLED, required = true)
    val metricsHistoryEnabled: Boolean,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    val enabled: Boolean,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true)
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromMonitorRecord(record: TcpMonitorRecord) = TcpMonitorDto(
            id = record.id,
            name = record.name,
            host = record.host,
            port = record.port,
            uptimeCheckInterval = record.uptimeCheckInterval,
            timeoutMs = record.timeoutMs,
            latencyThresholdMs = record.latencyThresholdMs,
            failureCountThreshold = record.failureCountThreshold,
            metricsHistoryEnabled = record.metricsHistoryEnabled,
            enabled = record.enabled,
            integrations = record.integrations.toSet(),
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }
}
