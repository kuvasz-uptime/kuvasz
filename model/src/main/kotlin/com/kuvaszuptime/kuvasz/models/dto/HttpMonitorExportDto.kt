package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected

@Introspected
data class HttpMonitorExportDto(
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
    val expectedStatusCodes: Set<Int>,
    val responseTimeThresholdMillis: Int?,
    val expectedKeyword: String?,
    val expectedKeywordCaseSensitive: Boolean,
    val expectedKeywordNegated: Boolean,
    val requestHeaders: Map<String, String>,
    val expectedHeaders: Map<String, String>,
    val requestBody: String?,
) {
    companion object {
        fun fromMonitorRecord(record: HttpMonitorRecord): HttpMonitorExportDto {
            return HttpMonitorExportDto(
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
                expectedStatusCodes = record.expectedStatusCodes.toSet(),
                responseTimeThresholdMillis = record.responseTimeThresholdMillis,
                expectedKeyword = record.expectedKeyword,
                expectedKeywordCaseSensitive = record.expectedKeywordCaseSensitive,
                expectedKeywordNegated = record.expectedKeywordNegated,
                requestHeaders = record.requestHeadersAsMap(),
                expectedHeaders = record.expectedHeadersAsMap(),
                requestBody = record.requestBody,
            )
        }
    }
}
