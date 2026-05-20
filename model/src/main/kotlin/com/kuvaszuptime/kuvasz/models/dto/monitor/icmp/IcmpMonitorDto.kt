package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class IcmpMonitorDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @param:Schema(description = IcmpMonitorDocs.HOST, required = true)
    val host: String,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @param:Schema(description = IcmpMonitorDocs.PACKET_COUNT, required = true)
    val packetCount: Int,
    @param:Schema(description = IcmpMonitorDocs.TIMEOUT_SECONDS, required = true)
    val timeoutSeconds: Int,
    @param:Schema(description = IcmpMonitorDocs.PACKET_LOSS_THRESHOLD, required = true)
    val packetLossThreshold: Int,
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
    @param:Schema(description = IcmpMonitorDocs.METRICS_HISTORY_ENABLED, required = true)
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
        fun fromMonitorRecord(record: IcmpMonitorRecord) = IcmpMonitorDto(
            id = record.id,
            name = record.name,
            host = record.host,
            uptimeCheckInterval = record.uptimeCheckInterval,
            packetCount = record.packetCount,
            timeoutSeconds = record.timeoutSeconds,
            packetLossThreshold = record.packetLossThreshold,
            failureCountThreshold = record.failureCountThreshold,
            metricsHistoryEnabled = record.metricsHistoryEnabled,
            enabled = record.enabled,
            integrations = record.integrations.toSet(),
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }
}
