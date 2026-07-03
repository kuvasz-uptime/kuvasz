package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class HttpMonitorImportAdapter(private val dto: HttpMonitorExportDto) : HttpMonitorCreator {
    override val name: String get() = dto.name
    override val url: String get() = dto.url
    override val sensitiveUrl: Boolean get() = dto.sensitiveUrl
    override val uptimeCheckInterval: Int get() = dto.uptimeCheckInterval
    override val enabled: Boolean get() = dto.enabled
    override val sslCheckEnabled: Boolean get() = dto.sslCheckEnabled
    override val requestMethod: HttpMethod get() = dto.requestMethod
    override val latencyHistoryEnabled: Boolean get() = dto.latencyHistoryEnabled
    override val forceNoCache: Boolean get() = dto.forceNoCache
    override val followRedirects: Boolean get() = dto.followRedirects
    override val sslExpiryThreshold: Int get() = dto.sslExpiryThreshold
    override val failureCountThreshold: Long get() = dto.failureCountThreshold
    override val integrations: List<String>? get() = dto.integrations.map { it.toString() }
    override val expectedStatusCodes: List<Int>? get() = dto.expectedStatusCodes.toList()
    override val responseTimeThresholdMillis: Int? get() = dto.responseTimeThresholdMillis
    override val expectedKeyword: String? get() = dto.expectedKeyword
    override val expectedKeywordCaseSensitive: Boolean get() = dto.expectedKeywordCaseSensitive
    override val expectedKeywordNegated: Boolean get() = dto.expectedKeywordNegated
    override val requestHeaders: Map<String, String>? get() = dto.requestHeaders
    override val expectedHeaders: Map<String, String>? get() = dto.expectedHeaders
    override val requestBody: String? get() = dto.requestBody
}
