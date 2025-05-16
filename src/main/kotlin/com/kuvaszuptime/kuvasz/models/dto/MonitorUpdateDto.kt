package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern

@Introspected
data class MonitorUpdateDto(
    @field:Schema(required = false, nullable = false)
    @get:NotBlank
    @get:NotNull
    val name: String?,
    @field:Schema(required = false, nullable = false)
    @get:Pattern(regexp = URI_REGEX)
    @get:NotNull
    val url: String?,
    @get:Min(MIN_UPTIME_CHECK_INTERVAL)
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val uptimeCheckInterval: Int?,
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val enabled: Boolean?,
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val sslCheckEnabled: Boolean?,
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val requestMethod: HttpMethod?,
    @field:Schema(required = false, nullable = true)
    val pagerdutyIntegrationKey: String?,
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val latencyHistoryEnabled: Boolean?,
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val forceNoCache: Boolean?,
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val followRedirects: Boolean?,
    @get:NotNull
    @field:Schema(required = false, nullable = false)
    val sslExpiryThreshold: Int?,
)
