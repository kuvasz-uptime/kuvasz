package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
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
    val requestMethod: HttpMethod,
    val latencyHistoryEnabled: Boolean,
    val forceNoCache: Boolean,
    val followRedirects: Boolean,
    val sslExpiryThreshold: Int,
    val integrations: Set<IntegrationID>,
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
                requestMethod = record.requestMethod,
                latencyHistoryEnabled = record.latencyHistoryEnabled,
                forceNoCache = record.forceNoCache,
                followRedirects = record.followRedirects,
                sslExpiryThreshold = record.sslExpiryThreshold,
                integrations = record.integrations.toSet(),
                createdAt = record.createdAt,
                updatedAt = record.updatedAt,
            )
    }
}
