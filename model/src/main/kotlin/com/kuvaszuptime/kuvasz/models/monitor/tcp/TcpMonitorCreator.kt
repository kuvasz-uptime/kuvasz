package com.kuvaszuptime.kuvasz.models.monitor.tcp

import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorCreator
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

@Suppress("ComplexInterface")
interface TcpMonitorCreator : MonitorCreator<TcpMonitorRecord> {
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotBlank(message = MonitorValidationMessages.HOST_NOT_BLANK)
    val host: String

    @get:NotNull(message = MonitorValidationMessages.PORT_NOT_NULL)
    @get:Min(Validation.MIN_PORT, message = MonitorValidationMessages.PORT_MIN)
    @get:Max(Validation.MAX_PORT, message = MonitorValidationMessages.PORT_MAX)
    val port: Int

    @get:NotNull(message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL, message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    val uptimeCheckInterval: Int

    @get:NotNull(message = MonitorValidationMessages.TIMEOUT_MILLIS_NOT_NULL)
    @get:Min(Validation.MIN_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MIN)
    @get:Max(Validation.MAX_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MAX)
    val timeoutMs: Int

    @get:Min(Validation.MIN_LATENCY_THRESHOLD_MILLIS, message = MonitorValidationMessages.LATENCY_THRESHOLD_MIN)
    val latencyThresholdMs: Int?

    @get:NotNull(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_NOT_NULL)
    @get:Positive(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE)
    val failureCountThreshold: Long

    val enabled: Boolean
    override val integrations: List<String>?
    val metricsHistoryEnabled: Boolean
    override fun toMonitorRecord(validatedIntegrations: Set<IntegrationID>): TcpMonitorRecord =
        TcpMonitorRecord()
            .setName(name)
            .setHost(host)
            .setPort(port)
            .setUptimeCheckInterval(uptimeCheckInterval)
            .setTimeoutMs(timeoutMs)
            .setLatencyThresholdMs(latencyThresholdMs)
            .setFailureCountThreshold(failureCountThreshold)
            .setEnabled(enabled)
            .setIntegrations(validatedIntegrations.toTypedArray())
            .setMetricsHistoryEnabled(metricsHistoryEnabled)
}
