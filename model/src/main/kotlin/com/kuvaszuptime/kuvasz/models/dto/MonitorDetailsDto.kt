package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.type
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.net.URI
import java.time.OffsetDateTime

@Introspected
data class MonitorDetailsDto(
    @param:Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @param:Schema(description = MonitorDocs.URL, required = true)
    val url: URI,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @param:Schema(description = MonitorDocs.ENABLED, required = true)
    val enabled: Boolean,
    @param:Schema(description = MonitorDocs.SSL_CHECK_ENABLED, required = true)
    val sslCheckEnabled: Boolean,
    @param:Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MonitorDocs.UPDATED_AT, required = true, nullable = true)
    val updatedAt: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS, required = true, nullable = true)
    val uptimeStatus: UptimeStatus?,
    @param:Schema(description = MonitorDocs.UPTIME_STATUS_STARTED_AT, required = true, nullable = true)
    val uptimeStatusStartedAt: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.LAST_UPTIME_CHECK, required = true, nullable = true)
    val lastUptimeCheck: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.NEXT_UPTIME_CHECK, required = true, nullable = true)
    val nextUptimeCheck: OffsetDateTime? = null,
    @param:Schema(description = MonitorDocs.SSL_STATUS, required = true, nullable = true)
    val sslStatus: SslStatus?,
    @param:Schema(description = MonitorDocs.SSL_STATUS_STARTED_AT, required = true, nullable = true)
    val sslStatusStartedAt: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.LAST_SSL_CHECK, required = true, nullable = true)
    val lastSSLCheck: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.NEXT_SSL_CHECK, required = true, nullable = true)
    val nextSSLCheck: OffsetDateTime? = null,
    @param:Schema(description = MonitorDocs.UPTIME_ERROR, required = true, nullable = true)
    val uptimeError: String?,
    @param:Schema(description = MonitorDocs.SSL_ERROR, required = true, nullable = true)
    val sslError: String?,
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
    @param:Schema(description = MonitorDocs.SSL_VALID_UNTIL, required = true, nullable = true)
    val sslValidUntil: OffsetDateTime?,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MonitorDocs.EFFECTIVE_INTEGRATIONS, required = true)
    val effectiveIntegrations: Set<IntegrationDetailsDto>,
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
    @param:Schema(description = MonitorDocs.REQUEST_HEADERS, required = true)
    val requestHeaders: Map<String, String>,
    @param:Schema(description = MonitorDocs.EXPECTED_HEADERS, required = true)
    val expectedHeaders: Map<String, String>,
    @param:Schema(description = MonitorDocs.REQUEST_BODY, required = true, nullable = true)
    val requestBody: String? = null,
)

data class IntegrationDetailsDto(
    @param:Schema(
        description = "Unique identifier for the integration that can be used as a reference on a monitor",
        required = true,
    )
    val id: String,
    @param:Schema(description = "Type of the integration, e.g., EMAIL, WEBHOOK, etc.", required = true)
    val type: IntegrationType,
    @param:Schema(
        description = "Name of the integration, e.g., 'slack-team-devops', 'email-ops-global', etc.",
        required = true,
    )
    val name: String,
    @param:Schema(description = "Whether the integration is enabled or not", required = true)
    val enabled: Boolean,
    @param:Schema(description = "Whether the integration is global or not", required = true)
    val global: Boolean,
) {
    companion object {
        fun fromConfig(config: IntegrationConfig): IntegrationDetailsDto = IntegrationDetailsDto(
            id = config.id.toString(),
            type = config.type,
            name = config.name,
            enabled = config.enabled,
            global = config.global,
        )
    }
}
