package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.Validation.MIN_HEARTBEAT_INTERVAL
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

@Introspected
data class PushMonitorUpdateDto(
    @param:Schema(description = MonitorDocs.NAME, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String?,

    @get:Min(MIN_HEARTBEAT_INTERVAL, message = MonitorValidationMessages.HEARTBEAT_INTERVAL_MIN)
    @get:NotNull(message = MonitorValidationMessages.HEARTBEAT_INTERVAL_NOT_NULL)
    @param:Schema(description = PushMonitorDocs.HEARTBEAT_INTERVAL, required = false, nullable = false)
    val heartbeatInterval: Long?,

    @get:PositiveOrZero(message = MonitorValidationMessages.GRACE_PERIOD_POSITIVE_OR_ZERO)
    @get:NotNull(message = MonitorValidationMessages.GRACE_PERIOD_NOT_NULL)
    @param:Schema(description = PushMonitorDocs.GRACE_PERIOD, required = false, nullable = false)
    val gracePeriod: Long?,

    @get:NotNull(message = MonitorValidationMessages.CLIENT_SECRET_NOT_NULL)
    @get:NotBlank(message = MonitorValidationMessages.CLIENT_SECRET_NOT_BLANK)
    @get:Size(min = Validation.MIN_CLIENT_SECRET_LENGTH, message = MonitorValidationMessages.CLIENT_SECRET_MIN_LENGTH)
    @param:Schema(description = PushMonitorDocs.CLIENT_SECRET, required = false, nullable = false)
    val clientSecret: String?,

    @get:NotNull
    @param:Schema(description = MonitorDocs.ENABLED, required = false, nullable = false)
    val enabled: Boolean?,

    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false, nullable = true)
    val integrations: Set<IntegrationID>?,

    @get:NotNull
    @get:Positive(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE)
    @param:Schema(description = MonitorDocs.FAILURE_COUNT_THRESHOLD, required = false, nullable = false)
    val failureCountThreshold: Long?,
)
