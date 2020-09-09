package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_UPTIME_CHECK_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.Validation.URI_REGEX
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import io.micronaut.core.annotation.Introspected
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern

@Introspected
data class MonitorCreateDto(
    @get:NotBlank
    val name: String,
    @get:NotNull
    @get:Pattern(regexp = URI_REGEX)
    val url: String,
    @get:NotNull
    @get:Min(MIN_UPTIME_CHECK_INTERVAL)
    val uptimeCheckInterval: Int,
    val enabled: Boolean? = true,
    val sslCheckEnabled: Boolean? = false
) {
    fun toMonitorPojo(): MonitorPojo = MonitorPojo()
        .setName(name)
        .setUrl(url)
        .setEnabled(enabled)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setSslCheckEnabled(sslCheckEnabled)
}
