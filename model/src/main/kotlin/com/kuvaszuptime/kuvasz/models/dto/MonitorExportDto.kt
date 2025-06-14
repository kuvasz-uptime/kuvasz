package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected

@Introspected
data class MonitorExportDto(
    val name: String,
    val url: String,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val latencyHistoryEnabled: Boolean,
    val requestMethod: HttpMethod,
    val followRedirects: Boolean,
    val forceNoCache: Boolean,
    val sslExpiryThreshold: Int,
    val integrations: Set<IntegrationID>,
) {
    companion object {
        fun fromMonitorRecord(record: MonitorRecord): MonitorExportDto {
            return MonitorExportDto(
                name = record.name,
                url = record.url,
                uptimeCheckInterval = record.uptimeCheckInterval,
                enabled = record.enabled,
                sslCheckEnabled = record.sslCheckEnabled,
                latencyHistoryEnabled = record.latencyHistoryEnabled,
                requestMethod = record.requestMethod,
                followRedirects = record.followRedirects,
                forceNoCache = record.forceNoCache,
                sslExpiryThreshold = record.sslExpiryThreshold,
                integrations = record.integrations.toSet(),
            )
        }
    }
}
