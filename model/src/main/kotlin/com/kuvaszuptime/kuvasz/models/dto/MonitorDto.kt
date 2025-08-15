package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class MonitorDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @param:Schema(description = MonitorDocs.URL, required = true)
    val url: String,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    val enabled: Boolean,
    @param:Schema(description = MonitorDocs.SSL_CHECK_ENABLED, required = true)
    val sslCheckEnabled: Boolean,
    @param:Schema(description = MonitorDocs.REQUEST_METHOD, required = true)
    val requestMethod: HttpMethod,
    @param:Schema(description = MonitorDocs.LATENCY_HISTORY_ENABLED, required = true)
    val latencyHistoryEnabled: Boolean,
    @param:Schema(description = MonitorDocs.FORCE_NO_CACHE, required = true)
    val forceNoCache: Boolean,
    @param:Schema(description = MonitorDocs.FOLLOW_REDIRECTS, required = true)
    val followRedirects: Boolean,
    @param:Schema(description = MonitorDocs.SSL_EXPIRY_THRESHOLD, required = true)
    val sslExpiryThreshold: Int,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.EXPECTED_STATUS_CODES, required = true)
    val expectedStatusCodes: Set<Int>,
    @param:Schema(description = MonitorDocs.RESPONSE_TIME_THRESHOLD, required = true, nullable = true)
    val responseTimeThresholdMillis: Int? = null,
    @param:Schema(description = MonitorDocs.EXPECTED_KEYWORD, required = true, nullable = true)
    val expectedKeyword: String? = null,
    @param:Schema(description = MonitorDocs.EXPECTED_KEYWORD_CASE_SENSITIVE, required = true)
    val expectedKeywordCaseSensitive: Boolean,
    @param:Schema(description = MonitorDocs.EXPECTED_KEYWORD_NEGATED, required = true)
    val expectedKeywordNegated: Boolean,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true, nullable = true)
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
                expectedStatusCodes = record.expectedStatusCodes.toSet(),
                responseTimeThresholdMillis = record.responseTimeThresholdMillis,
                expectedKeyword = record.expectedKeyword,
                expectedKeywordCaseSensitive = record.expectedKeywordCaseSensitive,
                expectedKeywordNegated = record.expectedKeywordNegated,
                createdAt = record.createdAt,
                updatedAt = record.updatedAt,
            )
    }
}
