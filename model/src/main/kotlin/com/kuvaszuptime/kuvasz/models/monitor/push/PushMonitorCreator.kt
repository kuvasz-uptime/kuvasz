package com.kuvaszuptime.kuvasz.models.monitor.push

import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size

@Suppress("ComplexInterface")
interface PushMonitorCreator {
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotNull(message = MonitorValidationMessages.HEARTBEAT_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_HEARTBEAT_INTERVAL, message = MonitorValidationMessages.HEARTBEAT_INTERVAL_MIN)
    val heartbeatInterval: Long

    @get:NotNull(message = MonitorValidationMessages.GRACE_PERIOD_NOT_NULL)
    @get:PositiveOrZero(message = MonitorValidationMessages.GRACE_PERIOD_POSITIVE_OR_ZERO)
    val gracePeriod: Long

    @get:NotNull(message = MonitorValidationMessages.CLIENT_SECRET_NOT_NULL)
    @get:NotBlank(message = MonitorValidationMessages.CLIENT_SECRET_NOT_BLANK)
    @get:Size(min = Validation.MIN_CLIENT_SECRET_LENGTH, message = MonitorValidationMessages.CLIENT_SECRET_MIN_LENGTH)
    val clientSecret: String

    val enabled: Boolean
    val integrations: List<String>?
}

fun PushMonitorCreator.toMonitorRecord(validatedIntegrations: Set<IntegrationID>): PushMonitorRecord =
    PushMonitorRecord()
        .setName(name)
        .setHeartbeatInterval(heartbeatInterval)
        .setGracePeriod(gracePeriod)
        .setEnabled(enabled)
        .setClientSecret(clientSecret)
        .setIntegrations(validatedIntegrations.toTypedArray())
