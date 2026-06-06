package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.monitor.IntegrationDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class PushMonitorDetailsDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    override val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = PushMonitorDocs.HEARTBEAT_INTERVAL, required = true)
    val heartbeatInterval: Long,
    @param:Schema(description = PushMonitorDocs.GRACE_PERIOD, required = true)
    val gracePeriod: Long,
    @param:Schema(description = PushMonitorDocs.CLIENT_SECRET, required = true)
    val clientSecret: String,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true, nullable = true)
    val updatedAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS, required = true, nullable = true)
    override val uptimeStatus: UptimeStatus?,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS_STARTED_AT, required = true, nullable = true)
    val uptimeStatusStartedAt: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.LAST_UPTIME_CHECK, required = true, nullable = true)
    val lastUptimeCheck: OffsetDateTime?,
    @param:Schema(description = PushMonitorDocs.LAST_HEARTBEAT, required = true, nullable = true)
    val lastHeartbeat: OffsetDateTime?,
    @param:Schema(description = PushMonitorDocs.NEXT_EXPECTED_HEARTBEAT, required = true, nullable = true)
    val nextExpectedHeartbeat: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.UPTIME_ERROR, required = true, nullable = true)
    override val uptimeError: String?,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.EFFECTIVE_INTEGRATIONS, required = true)
    val effectiveIntegrations: Set<IntegrationDetailsDto>,
    @param:Schema(description = MonitorDocs.STATUS_PAGES, required = true)
    val statusPages: Set<String>,
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
) : MonitorDetailsDto
