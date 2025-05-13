package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.enums.HttpMethod
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

@Introspected
data class MonitorUpdateDto(
    @get:NotBlank
    val name: String?,
    @get:Pattern(regexp = URI_REGEX)
    val url: String?,
    @get:Min(MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int?,
    val enabled: Boolean?,
    val sslCheckEnabled: Boolean?,
    val requestMethod: HttpMethod?,
    val pagerdutyIntegrationKey: String?,
    val latencyHistoryEnabled: Boolean?,
    val forceNoCache: Boolean?,
    val followRedirects: Boolean?,
)
