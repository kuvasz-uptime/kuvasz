package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class DockerMonitorDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @param:Schema(description = DockerMonitorDocs.DOCKER_HOST, required = true)
    val dockerHost: String,
    @param:Schema(description = DockerMonitorDocs.CONTAINER, required = true)
    val container: String,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @param:Schema(description = DockerMonitorDocs.TIMEOUT_MS, required = true)
    val timeoutMs: Int,
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
    @param:Schema(description = DockerMonitorDocs.METRICS_HISTORY_ENABLED, required = true)
    val metricsHistoryEnabled: Boolean,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    val enabled: Boolean,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true)
    val updatedAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.CATEGORY, required = true, nullable = true)
    val category: String? = null,
    @param:Schema(description = MonitorDocs.IGNORE_CONNECTIVITY_CHECK, required = true)
    val ignoreConnectivityCheck: Boolean,
) {
    companion object {
        fun fromMonitorRecord(record: DockerMonitorRecord) = DockerMonitorDto(
            id = record.id,
            name = record.name,
            dockerHost = record.dockerHost,
            container = record.container,
            uptimeCheckInterval = record.uptimeCheckInterval,
            timeoutMs = record.timeoutMs,
            failureCountThreshold = record.failureCountThreshold,
            metricsHistoryEnabled = record.metricsHistoryEnabled,
            enabled = record.enabled,
            integrations = record.integrations.toSet(),
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            category = record.category,
            ignoreConnectivityCheck = record.ignoreConnectivityCheck,
        )
    }
}
