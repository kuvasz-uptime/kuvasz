package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import io.micronaut.core.annotation.Introspected
import java.time.OffsetDateTime

@Introspected
data class MonitorDto(
    val id: Long,
    val name: String,
    val url: String,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val pagerdutyKeyPresent: Boolean,
    val requestMethod: HttpMethod,
    val latencyHistoryEnabled: Boolean,
    val forceNoCache: Boolean,
    val followRedirects: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?
) {
    companion object {
        fun fromMonitorRecord(record: MonitorRecord) =
            MonitorDto(
                id = record.id,
                name = record.name,
                url = record.url,
                uptimeCheckInterval = record.uptimeCheckInterval,
                enabled = record.enabled,
                sslCheckEnabled = record.sslCheckEnabled,
                pagerdutyKeyPresent = !record.pagerdutyIntegrationKey.isNullOrBlank(),
                requestMethod = record.requestMethod,
                latencyHistoryEnabled = record.latencyHistoryEnabled,
                forceNoCache = record.forceNoCache,
                followRedirects = record.followRedirects,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt
            )
    }
}
