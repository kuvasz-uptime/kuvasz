package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

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
data class IcmpMonitorUpdateDto(
    @param:Schema(description = MonitorDocs.NAME, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String?,

    @param:Schema(description = IcmpMonitorDocs.HOST, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.HOST_NOT_BLANK)
    val host: String?,

    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL, message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    val uptimeCheckInterval: Int?,

    @param:Schema(description = IcmpMonitorDocs.PACKET_COUNT, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.PACKET_COUNT_NOT_NULL)
    @get:Min(Validation.MIN_PACKET_COUNT, message = MonitorValidationMessages.PACKET_COUNT_MIN)
    @get:Max(Validation.MAX_PACKET_COUNT, message = MonitorValidationMessages.PACKET_COUNT_MAX)
    val packetCount: Int?,

    @param:Schema(description = IcmpMonitorDocs.TIMEOUT_SECONDS, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.TIMEOUT_SECONDS_NOT_NULL)
    @get:Min(Validation.MIN_TIMEOUT_SECONDS, message = MonitorValidationMessages.TIMEOUT_SECONDS_MIN)
    @get:Max(Validation.MAX_TIMEOUT_SECONDS, message = MonitorValidationMessages.TIMEOUT_SECONDS_MAX)
    val timeoutSeconds: Int?,

    @param:Schema(description = IcmpMonitorDocs.PACKET_LOSS_THRESHOLD, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.PACKET_LOSS_THRESHOLD_NOT_NULL)
    @get:Min(
        Validation.MIN_PACKET_LOSS_THRESHOLD,
        message = MonitorValidationMessages.PACKET_LOSS_THRESHOLD_MIN
    )
    @get:Max(
        Validation.MAX_PACKET_LOSS_THRESHOLD,
        message = MonitorValidationMessages.PACKET_LOSS_THRESHOLD_MAX
    )
    val packetLossThreshold: Int?,

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
    @param:Schema(description = IcmpMonitorDocs.METRICS_HISTORY_ENABLED, required = false, nullable = false)
    val metricsHistoryEnabled: Boolean?,
)
