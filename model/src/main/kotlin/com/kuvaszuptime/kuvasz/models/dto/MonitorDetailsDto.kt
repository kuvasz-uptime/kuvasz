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
    @Schema(description = MonitorDocs.ID, required = true)
    val id: Long,
    @Schema(description = MonitorDocs.NAME, required = true)
    val name: String,
    @Schema(description = MonitorDocs.URL, required = true)
    val url: URI,
    @Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    val uptimeCheckInterval: Int,
    @Schema(description = MonitorDocs.ENABLED, required = true)
    val enabled: Boolean,
    @Schema(description = MonitorDocs.SSL_CHECK_ENABLED, required = true)
    val sslCheckEnabled: Boolean,
    @Schema(description = MonitorDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @Schema(description = MonitorDocs.UPDATED_AT, required = true, nullable = true)
    val updatedAt: OffsetDateTime?,
    @Schema(description = MonitorDocs.UPTIME_STATUS, required = true, nullable = true)
    val uptimeStatus: UptimeStatus?,
    @Schema(description = MonitorDocs.UPTIME_STATUS_STARTED_AT, required = true, nullable = true)
    val uptimeStatusStartedAt: OffsetDateTime?,
    @Schema(description = MonitorDocs.LAST_UPTIME_CHECK, required = true, nullable = true)
    val lastUptimeCheck: OffsetDateTime?,
    @Schema(description = MonitorDocs.NEXT_UPTIME_CHECK, required = true, nullable = true)
    val nextUptimeCheck: OffsetDateTime? = null,
    @Schema(description = MonitorDocs.SSL_STATUS, required = true, nullable = true)
    val sslStatus: SslStatus?,
    @Schema(description = MonitorDocs.SSL_STATUS_STARTED_AT, required = true, nullable = true)
    val sslStatusStartedAt: OffsetDateTime?,
    @Schema(description = MonitorDocs.LAST_SSL_CHECK, required = true, nullable = true)
    val lastSSLCheck: OffsetDateTime?,
    @Schema(description = MonitorDocs.NEXT_SSL_CHECK, required = true, nullable = true)
    val nextSSLCheck: OffsetDateTime? = null,
    @Schema(description = MonitorDocs.UPTIME_ERROR, required = true, nullable = true)
    val uptimeError: String?,
    @Schema(description = MonitorDocs.SSL_ERROR, required = true, nullable = true)
    val sslError: String?,
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
    @Schema(description = MonitorDocs.SSL_VALID_UNTIL, required = true, nullable = true)
    val sslValidUntil: OffsetDateTime?,
    @Schema(description = MonitorDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @Schema(description = MonitorDocs.EFFECTIVE_INTEGRATIONS, required = true)
    val effectiveIntegrations: Set<IntegrationDetailsDto>,
)

data class IntegrationDetailsDto(
    @Schema(
        description = "Unique identifier for the integration that can be used as a reference on a monitor",
        required = true,
    )
    val id: String,
    @Schema(description = "Type of the integration, e.g., EMAIL, WEBHOOK, etc.", required = true)
    val type: IntegrationType,
    @Schema(
        description = "Name of the integration, e.g., 'slack-team-devops', 'email-ops-global', etc.",
        required = true,
    )
    val name: String,
    @Schema(description = "Whether the integration is enabled or not", required = true)
    val enabled: Boolean,
    @Schema(description = "Whether the integration is global or not", required = true)
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
