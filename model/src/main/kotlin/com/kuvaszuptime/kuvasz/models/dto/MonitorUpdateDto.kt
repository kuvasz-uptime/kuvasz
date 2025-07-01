package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

@Introspected
data class MonitorUpdateDto(
    @Schema(description = MonitorDocs.NAME, required = false, nullable = false)
    @get:NotBlank
    @get:NotNull
    val name: String?,
    @Schema(description = MonitorDocs.URL, required = false, nullable = false)
    @get:Pattern(regexp = URI_REGEX)
    @get:NotNull
    val url: String?,
    @get:Min(MIN_UPTIME_CHECK_INTERVAL)
    @get:NotNull
    @Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = false, nullable = false)
    val uptimeCheckInterval: Int?,
    @get:NotNull
    @Schema(description = MonitorDocs.ENABLED, required = false, nullable = false)
    val enabled: Boolean?,
    @get:NotNull
    @Schema(description = MonitorDocs.SSL_CHECK_ENABLED, required = false, nullable = false)
    val sslCheckEnabled: Boolean?,
    @get:NotNull
    @Schema(description = MonitorDocs.REQUEST_METHOD, required = false, nullable = false)
    val requestMethod: HttpMethod?,
    @get:NotNull
    @Schema(description = MonitorDocs.LATENCY_HISTORY_ENABLED, required = false, nullable = false)
    val latencyHistoryEnabled: Boolean?,
    @get:NotNull
    @Schema(description = MonitorDocs.FORCE_NO_CACHE, required = false, nullable = false)
    val forceNoCache: Boolean?,
    @get:NotNull
    @Schema(description = MonitorDocs.FOLLOW_REDIRECTS, required = false, nullable = false)
    val followRedirects: Boolean?,
    @get:NotNull
    @Schema(description = MonitorDocs.SSL_EXPIRY_THRESHOLD, required = false, nullable = false)
    val sslExpiryThreshold: Int?,
    @Schema(description = MonitorDocs.INTEGRATIONS, required = false, nullable = true)
    val integrations: Set<IntegrationID>?,
)
