package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class HttpMonitorImportAdapter(dto: HttpMonitorExportDto) : HttpMonitorCreator {
    override val name: String = dto.name
    override val url: String = dto.url
    override val sensitiveUrl: Boolean = dto.sensitiveUrl
    override val uptimeCheckInterval: Int = dto.uptimeCheckInterval
    override val enabled: Boolean = dto.enabled
    override val sslCheckEnabled: Boolean = dto.sslCheckEnabled
    override val requestMethod: HttpMethod = dto.requestMethod
    override val latencyHistoryEnabled: Boolean = dto.latencyHistoryEnabled
    override val forceNoCache: Boolean = dto.forceNoCache
    override val followRedirects: Boolean = dto.followRedirects
    override val sslExpiryThreshold: Int = dto.sslExpiryThreshold
    override val failureCountThreshold: Long = dto.failureCountThreshold
    override val integrations: List<String> = dto.integrations.map { it.toString() }
    override val expectedStatusCodes: List<Int> = dto.expectedStatusCodes.toList()
    override val responseTimeThresholdMillis: Int? = dto.responseTimeThresholdMillis
    override val expectedKeyword: String? = dto.expectedKeyword
    override val expectedKeywordCaseSensitive: Boolean = dto.expectedKeywordCaseSensitive
    override val expectedKeywordNegated: Boolean = dto.expectedKeywordNegated
    override val requestHeaders: Map<String, String> = dto.requestHeaders
    override val expectedHeaders: Map<String, String> = dto.expectedHeaders
    override val requestBody: String? = dto.requestBody
    override val category: String? = dto.category
}
