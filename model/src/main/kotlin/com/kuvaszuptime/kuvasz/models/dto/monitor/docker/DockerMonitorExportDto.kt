package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected

@Introspected
data class DockerMonitorExportDto(
    val name: String,
    val dockerHost: String,
    val container: String,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val failureCountThreshold: Long,
    val enabled: Boolean,
    val integrations: Set<IntegrationID>,
    val metricsHistoryEnabled: Boolean,
    val category: String? = null,
    val ignoreConnectivityCheck: Boolean = DockerMonitorDefaults.IGNORE_CONNECTIVITY_CHECK,
) {
    companion object {
        fun fromMonitorRecord(record: DockerMonitorRecord): DockerMonitorExportDto {
            return DockerMonitorExportDto(
                name = record.name,
                dockerHost = record.dockerHost,
                container = record.container,
                uptimeCheckInterval = record.uptimeCheckInterval,
                timeoutMs = record.timeoutMs,
                failureCountThreshold = record.failureCountThreshold,
                enabled = record.enabled,
                integrations = record.integrations.toSet(),
                metricsHistoryEnabled = record.metricsHistoryEnabled,
                category = record.category,
                ignoreConnectivityCheck = record.ignoreConnectivityCheck,
            )
        }
    }
}
