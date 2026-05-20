package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class PushMonitorDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @param:Schema(description = PushMonitorDocs.HEARTBEAT_INTERVAL, required = true)
    val heartbeatInterval: Long,
    @param:Schema(description = PushMonitorDocs.GRACE_PERIOD, required = true)
    val gracePeriod: Long,
    @param:Schema(description = PushMonitorDocs.LAST_HEARTBEAT, required = true, nullable = true)
    val lastHeartbeat: OffsetDateTime?,
    @param:Schema(description = PushMonitorDocs.CLIENT_SECRET, required = true)
    val clientSecret: String,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    val enabled: Boolean,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true, nullable = true)
    val updatedAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
) {
    companion object {
        fun fromMonitorRecord(record: PushMonitorRecord) =
            PushMonitorDto(
                id = record.id,
                name = record.name,
                heartbeatInterval = record.heartbeatInterval,
                gracePeriod = record.gracePeriod,
                lastHeartbeat = record.lastHeartbeat,
                enabled = record.enabled,
                integrations = record.integrations.toSet(),
                clientSecret = record.clientSecret,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt,
                failureCountThreshold = record.failureCountThreshold,
            )
    }
}
