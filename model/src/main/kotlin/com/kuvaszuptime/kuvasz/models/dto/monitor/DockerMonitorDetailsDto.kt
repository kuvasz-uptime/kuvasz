package com.kuvaszuptime.kuvasz.models.dto.monitor

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.docker.DockerMonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class DockerMonitorDetailsDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    override val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = DockerMonitorDocs.DOCKER_HOST, required = true)
    val dockerHost: String,
    @param:Schema(description = DockerMonitorDocs.CONTAINER, required = true)
    val container: String,
    @param:Schema(description = DockerMonitorDocs.IMAGE, required = true, nullable = true)
    val image: String?,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @param:Schema(description = DockerMonitorDocs.TIMEOUT_MS, required = true)
    val timeoutMs: Int,
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
    @param:Schema(description = DockerMonitorDocs.METRICS_HISTORY_ENABLED, required = true)
    val metricsHistoryEnabled: Boolean,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true)
    val updatedAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS, required = true, nullable = true)
    override val uptimeStatus: UptimeStatus?,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS_STARTED_AT, required = true, nullable = true)
    override val uptimeStatusStartedAt: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.LAST_UPTIME_CHECK, required = true, nullable = true)
    override val lastUptimeCheck: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.NEXT_UPTIME_CHECK, required = true, nullable = true)
    val nextUptimeCheck: OffsetDateTime? = null,
    @param:Schema(description = MonitorDocs.UPTIME_ERROR, required = true, nullable = true)
    override val uptimeError: String?,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.EFFECTIVE_INTEGRATIONS, required = true)
    val effectiveIntegrations: Set<IntegrationDetailsDto>,
    @param:Schema(description = MonitorDocs.STATUS_PAGES, required = true)
    override val statusPages: Set<String>,
    @param:Schema(description = MonitorDocs.MAINTENANCE_WINDOWS, required = true)
    val maintenanceWindows: List<MaintenanceWindowDetailsDto>,
    @param:Schema(description = MonitorDocs.UNDER_MAINTENANCE, required = true)
    override val inMaintenance: Boolean,
    @param:Schema(description = MonitorDocs.CATEGORY, required = true, nullable = true)
    override val category: String?,
    @param:Schema(description = MonitorDocs.IGNORE_CONNECTIVITY_CHECK, required = true)
    override val ignoreConnectivityCheck: Boolean,
) : MonitorDetailsDto
