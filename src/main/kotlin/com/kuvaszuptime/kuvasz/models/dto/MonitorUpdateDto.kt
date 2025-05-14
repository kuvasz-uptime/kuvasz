package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * An intermedia data class to generate the correct OpenAPI schema for monitor updates and also to make the incoming
 * updates easily validatable
 */
@Introspected
data class MonitorUpdateDto(
    @field:Schema(required = false, nullable = false)
    @get:NotBlank
    val name: String?,
    @field:Schema(required = false, nullable = false)
    @get:Pattern(regexp = URI_REGEX)
    val url: String?,
    @get:Min(MIN_UPTIME_CHECK_INTERVAL)
    @field:Schema(required = false, nullable = false)
    val uptimeCheckInterval: Int?,
    @field:Schema(required = false, nullable = false)
    val enabled: Boolean?,
    @field:Schema(required = false, nullable = false)
    val sslCheckEnabled: Boolean?,
    @field:Schema(required = false, nullable = false)
    val requestMethod: HttpMethod?,
    @field:Schema(required = false, nullable = true)
    val pagerdutyIntegrationKey: String?,
    @field:Schema(required = false, nullable = false)
    val latencyHistoryEnabled: Boolean?,
    @field:Schema(required = false, nullable = false)
    val forceNoCache: Boolean?,
    @field:Schema(required = false, nullable = false)
    val followRedirects: Boolean?,
)
