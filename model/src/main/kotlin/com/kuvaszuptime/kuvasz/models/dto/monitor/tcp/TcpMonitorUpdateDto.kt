package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

@Introspected
data class TcpMonitorUpdateDto(
    @param:Schema(description = MonitorDocs.NAME, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String?,

    @param:Schema(description = TcpMonitorDocs.HOST, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.HOST_NOT_BLANK)
    val host: String?,

    @param:Schema(description = TcpMonitorDocs.PORT, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.PORT_NOT_NULL)
    @get:Min(Validation.MIN_PORT, message = MonitorValidationMessages.PORT_MIN)
    @get:Max(Validation.MAX_PORT, message = MonitorValidationMessages.PORT_MAX)
    val port: Int?,

    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL, message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    val uptimeCheckInterval: Int?,

    @param:Schema(description = TcpMonitorDocs.TIMEOUT_MS, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.TIMEOUT_MILLIS_NOT_NULL)
    @get:Min(Validation.MIN_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MIN)
    @get:Max(Validation.MAX_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MAX)
    val timeoutMs: Int?,

    @param:Schema(description = TcpMonitorDocs.LATENCY_THRESHOLD_MS, required = false, nullable = true)
    @get:Min(Validation.MIN_LATENCY_THRESHOLD_MILLIS, message = MonitorValidationMessages.LATENCY_THRESHOLD_MIN)
    val latencyThresholdMs: Int?,

    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = false, nullable = false)
    @get:NotNull
    @get:Positive(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE)
    val failureCountThreshold: Long?,

    @param:Schema(description = MonitorDocs.ENABLED, required = false, nullable = false)
    @get:NotNull
    val enabled: Boolean?,

    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false, nullable = true)
    val integrations: Set<IntegrationID>?,

    @get:NotNull
    @param:Schema(description = TcpMonitorDocs.METRICS_HISTORY_ENABLED, required = false, nullable = false)
    val metricsHistoryEnabled: Boolean?,
)
