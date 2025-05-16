package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import io.micronaut.core.annotation.Introspected

@Introspected
data class MonitorExportDto(
    val name: String,
    val url: String,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val latencyHistoryEnabled: Boolean,
    val pagerdutyIntegrationKey: String?,
    val requestMethod: HttpMethod,
    val followRedirects: Boolean,
    val forceNoCache: Boolean,
    val sslExpiryThreshold: Int,
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
                pagerdutyIntegrationKey = record.pagerdutyIntegrationKey,
                requestMethod = record.requestMethod,
                followRedirects = record.followRedirects,
                forceNoCache = record.forceNoCache,
                sslExpiryThreshold = record.sslExpiryThreshold,
            )
        }
    }
}
