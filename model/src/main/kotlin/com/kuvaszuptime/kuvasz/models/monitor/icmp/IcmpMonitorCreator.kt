package com.kuvaszuptime.kuvasz.models.monitor.icmp

import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

@Suppress("ComplexInterface")
interface IcmpMonitorCreator {
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotBlank(message = MonitorValidationMessages.HOST_NOT_BLANK)
    val host: String

    @get:NotNull(message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL, message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    val uptimeCheckInterval: Int

    @get:NotNull(message = MonitorValidationMessages.PACKET_COUNT_NOT_NULL)
    @get:Min(Validation.MIN_PACKET_COUNT, message = MonitorValidationMessages.PACKET_COUNT_MIN)
    @get:Max(Validation.MAX_PACKET_COUNT, message = MonitorValidationMessages.PACKET_COUNT_MAX)
    val packetCount: Int

    @get:NotNull(message = MonitorValidationMessages.TIMEOUT_SECONDS_NOT_NULL)
    @get:Min(Validation.MIN_TIMEOUT_SECONDS, message = MonitorValidationMessages.TIMEOUT_SECONDS_MIN)
    @get:Max(Validation.MAX_TIMEOUT_SECONDS, message = MonitorValidationMessages.TIMEOUT_SECONDS_MAX)
    val timeoutSeconds: Int

    @get:NotNull(message = MonitorValidationMessages.PACKET_LOSS_THRESHOLD_NOT_NULL)
    @get:Min(
        Validation.MIN_PACKET_LOSS_THRESHOLD,
        message = MonitorValidationMessages.PACKET_LOSS_THRESHOLD_MIN
    )
    @get:Max(
        Validation.MAX_PACKET_LOSS_THRESHOLD,
        message = MonitorValidationMessages.PACKET_LOSS_THRESHOLD_MAX
    )
    val packetLossThreshold: Int

    @get:NotNull(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_NOT_NULL)
    @get:Positive(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE)
    val failureCountThreshold: Long

    val enabled: Boolean
    val integrations: List<String>?
    val metricsHistoryEnabled: Boolean
}

fun IcmpMonitorCreator.toMonitorRecord(validatedIntegrations: Set<IntegrationID>): IcmpMonitorRecord =
    IcmpMonitorRecord()
        .setName(name)
        .setHost(host)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setPacketCount(packetCount)
        .setTimeoutSeconds(timeoutSeconds)
        .setPacketLossThreshold(packetLossThreshold)
        .setFailureCountThreshold(failureCountThreshold)
        .setEnabled(enabled)
        .setIntegrations(validatedIntegrations.toTypedArray())
        .setMetricsHistoryEnabled(metricsHistoryEnabled)
