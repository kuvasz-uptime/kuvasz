package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.IntegrationDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class PushMonitorDetailsDto(
    @param:Schema(description = PushMonitorDocs.ID, required = true)
    override val id: Long,
    @param:Schema(description = PushMonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = PushMonitorDocs.HEARTBEAT_INTERVAL, required = true)
    val heartbeatInterval: Long,
    @param:Schema(description = PushMonitorDocs.GRACE_PERIOD, required = true)
    val gracePeriod: Long,
    @param:Schema(description = PushMonitorDocs.CLIENT_SECRET, required = true)
    val clientSecret: String,
    @param:Schema(description = PushMonitorDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = PushMonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = PushMonitorDocs.UPDATED_AT, required = true, nullable = true)
    val updatedAt: OffsetDateTime,
    @param:Schema(description = PushMonitorDocs.UPTIME_STATUS, required = true, nullable = true)
    override val uptimeStatus: UptimeStatus?,
    @param:Schema(description = PushMonitorDocs.UPTIME_STATUS_STARTED_AT, required = true, nullable = true)
    val uptimeStatusStartedAt: OffsetDateTime?,
    @param:Schema(description = PushMonitorDocs.LAST_UPTIME_CHECK, required = true, nullable = true)
    val lastUptimeCheck: OffsetDateTime?,
    @param:Schema(description = PushMonitorDocs.LAST_HEARTBEAT, required = true, nullable = true)
    val lastHeartbeat: OffsetDateTime?,
    @param:Schema(description = PushMonitorDocs.NEXT_EXPECTED_HEARTBEAT, required = true, nullable = true)
    val nextExpectedHeartbeat: OffsetDateTime?,
    @param:Schema(description = PushMonitorDocs.UPTIME_ERROR, required = true, nullable = true)
    override val uptimeError: String?,
    @param:Schema(description = PushMonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = PushMonitorDocs.EFFECTIVE_INTEGRATIONS, required = true)
    val effectiveIntegrations: Set<IntegrationDetailsDto>,
    @param:Schema(description = PushMonitorDocs.STATUS_PAGES, required = true)
    val statusPages: Set<String>,
    @param:Schema(description = PushMonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
) : MonitorDetailsDto
