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
import java.net.URI
import java.time.OffsetDateTime

@Introspected
data class MonitorDetailsDto(
    val id: Long,
    val name: String,
    val url: URI,
    val uptimeCheckInterval: Int,
    val enabled: Boolean,
    val sslCheckEnabled: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime?,
    val uptimeStatus: UptimeStatus?,
    val uptimeStatusStartedAt: OffsetDateTime?,
    val lastUptimeCheck: OffsetDateTime?,
    val nextUptimeCheck: OffsetDateTime? = null,
    val sslStatus: SslStatus?,
    val sslStatusStartedAt: OffsetDateTime?,
    val lastSSLCheck: OffsetDateTime?,
    val nextSSLCheck: OffsetDateTime? = null,
    val uptimeError: String?,
    val sslError: String?,
    val requestMethod: HttpMethod,
    val latencyHistoryEnabled: Boolean,
    val forceNoCache: Boolean,
    val followRedirects: Boolean,
    val sslExpiryThreshold: Int,
    val sslValidUntil: OffsetDateTime?,
    val integrations: Set<IntegrationID>,
    val effectiveIntegrations: Set<IntegrationDetailsDto>,
)

data class IntegrationDetailsDto(
    val id: String,
    val type: IntegrationType,
    val name: String,
    val enabled: Boolean,
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
