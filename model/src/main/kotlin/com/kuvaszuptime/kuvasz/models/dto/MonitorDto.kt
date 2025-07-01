package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class MonitorDto(
    @Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @Schema(description = MonitorDocs.URL, required = true)
    val url: String,
    @Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @Schema(description = MonitorDocs.ENABLED, required = true)
    val enabled: Boolean,
    @Schema(description = MonitorDocs.SSL_CHECK_ENABLED, required = true)
    val sslCheckEnabled: Boolean,
    @Schema(description = MonitorDocs.REQUEST_METHOD, required = true)
    val requestMethod: HttpMethod,
    @Schema(description = MonitorDocs.LATENCY_HISTORY_ENABLED, required = true)
    val latencyHistoryEnabled: Boolean,
    @Schema(description = MonitorDocs.FORCE_NO_CACHE, required = true)
    val forceNoCache: Boolean,
    @Schema(description = MonitorDocs.FOLLOW_REDIRECTS, required = true)
    val followRedirects: Boolean,
    @Schema(description = MonitorDocs.SSL_EXPIRY_THRESHOLD, required = true)
    val sslExpiryThreshold: Int,
    @Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @Schema(description = MonitorDocs.UPDATED_AT, required = true, nullable = true)
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
