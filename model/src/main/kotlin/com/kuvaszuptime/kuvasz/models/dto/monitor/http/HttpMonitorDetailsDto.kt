package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.IntegrationDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.net.URI
import java.time.OffsetDateTime

@Introspected
data class HttpMonitorDetailsDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    override val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = HttpMonitorDocs.URL, required = true)
    val url: URI,
    @param:Schema(description = HttpMonitorDocs.SENSITIVE_URL, required = true)
    val sensitiveUrl: Boolean,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = HttpMonitorDocs.SSL_CHECK_ENABLED, required = true)
    val sslCheckEnabled: Boolean,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true, nullable = true)
    val updatedAt: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS, required = true, nullable = true)
    override val uptimeStatus: UptimeStatus?,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS_STARTED_AT, required = true, nullable = true)
    val uptimeStatusStartedAt: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.LAST_UPTIME_CHECK, required = true, nullable = true)
    val lastUptimeCheck: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.NEXT_UPTIME_CHECK, required = true, nullable = true)
    val nextUptimeCheck: OffsetDateTime? = null,
    @param:Schema(description = HttpMonitorDocs.SSL_STATUS, required = true, nullable = true)
    val sslStatus: SslStatus?,
    @param:Schema(description = HttpMonitorDocs.SSL_STATUS_STARTED_AT, required = true, nullable = true)
    val sslStatusStartedAt: OffsetDateTime?,
    @param:Schema(description = HttpMonitorDocs.LAST_SSL_CHECK, required = true, nullable = true)
    val lastSSLCheck: OffsetDateTime?,
    @param:Schema(description = HttpMonitorDocs.NEXT_SSL_CHECK, required = true, nullable = true)
    val nextSSLCheck: OffsetDateTime? = null,
    @param:Schema(description = MonitorDocs.UPTIME_ERROR, required = true, nullable = true)
    override val uptimeError: String?,
    @param:Schema(description = HttpMonitorDocs.SSL_ERROR, required = true, nullable = true)
    val sslError: String?,
    @param:Schema(description = HttpMonitorDocs.REQUEST_METHOD, required = true)
    val requestMethod: HttpMethod,
    @param:Schema(description = HttpMonitorDocs.LATENCY_HISTORY_ENABLED, required = true)
    val latencyHistoryEnabled: Boolean,
    @param:Schema(description = HttpMonitorDocs.FORCE_NO_CACHE, required = true)
    val forceNoCache: Boolean,
    @param:Schema(description = HttpMonitorDocs.FOLLOW_REDIRECTS, required = true)
    val followRedirects: Boolean,
    @param:Schema(description = HttpMonitorDocs.SSL_EXPIRY_THRESHOLD, required = true)
    val sslExpiryThreshold: Int,
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = true)
    val failureCountThreshold: Long,
    @param:Schema(description = HttpMonitorDocs.SSL_VALID_UNTIL, required = true, nullable = true)
    val sslValidUntil: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.EFFECTIVE_INTEGRATIONS, required = true)
    val effectiveIntegrations: Set<IntegrationDetailsDto>,
    @param:Schema(description = HttpMonitorDocs.EXPECTED_STATUS_CODES, required = true)
    val expectedStatusCodes: Set<Int>,
    @param:Schema(description = HttpMonitorDocs.RESPONSE_TIME_THRESHOLD, required = true, nullable = true)
    val responseTimeThresholdMillis: Int? = null,
    @param:Schema(description = HttpMonitorDocs.EXPECTED_KEYWORD, required = true, nullable = true)
    val expectedKeyword: String? = null,
    @param:Schema(description = HttpMonitorDocs.EXPECTED_KEYWORD_CASE_SENSITIVE, required = true)
    val expectedKeywordCaseSensitive: Boolean,
    @param:Schema(description = HttpMonitorDocs.EXPECTED_KEYWORD_NEGATED, required = true)
    val expectedKeywordNegated: Boolean,
    @param:Schema(description = HttpMonitorDocs.REQUEST_HEADERS, required = true)
    val requestHeaders: Map<String, String>,
    @param:Schema(description = HttpMonitorDocs.EXPECTED_HEADERS, required = true)
    val expectedHeaders: Map<String, String>,
    @param:Schema(description = HttpMonitorDocs.REQUEST_BODY, required = true, nullable = true)
    val requestBody: String? = null,
    @param:Schema(description = MonitorDocs.STATUS_PAGES, required = true)
    val statusPages: Set<String>,
    @param:Schema(description = MonitorDocs.MAINTENANCE_WINDOWS, required = true)
    val maintenanceWindows: List<MaintenanceWindowDetailsDto>,
    @param:Schema(description = MonitorDocs.UNDER_MAINTENANCE, required = true)
    val inMaintenance: Boolean,
) : MonitorDetailsDto
