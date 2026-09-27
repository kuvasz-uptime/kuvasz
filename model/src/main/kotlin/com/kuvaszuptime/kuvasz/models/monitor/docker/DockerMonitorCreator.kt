package com.kuvaszuptime.kuvasz.models.monitor.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.normalizedCategory
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

@Suppress("ComplexInterface")
interface DockerMonitorCreator : MonitorCreator<DockerMonitorRecord> {
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotBlank(message = MonitorValidationMessages.DOCKER_HOST_NOT_BLANK)
    val dockerHost: String

    @get:NotBlank(message = MonitorValidationMessages.CONTAINER_NOT_BLANK)
    val container: String

    @get:NotNull(message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL, message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    val uptimeCheckInterval: Int

    @get:NotNull(message = MonitorValidationMessages.TIMEOUT_MILLIS_NOT_NULL)
    @get:Min(Validation.MIN_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MIN)
    @get:Max(Validation.MAX_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MAX)
    val timeoutMs: Int

    @get:NotNull(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_NOT_NULL)
    @get:Positive(message = MonitorValidationMessages.FAILURE_COUNT_THRESHOLD_POSITIVE)
    val failureCountThreshold: Long

    val enabled: Boolean
    override val integrations: List<String>?
    val metricsHistoryEnabled: Boolean

    override fun toMonitorRecord(validatedIntegrations: Set<IntegrationID>): DockerMonitorRecord =
        DockerMonitorRecord()
            .setName(name)
            .setDockerHost(dockerHost)
            .setContainer(container)
            .setUptimeCheckInterval(uptimeCheckInterval)
            .setTimeoutMs(timeoutMs)
            .setFailureCountThreshold(failureCountThreshold)
            .setEnabled(enabled)
            .setIntegrations(validatedIntegrations.toTypedArray())
            .setMetricsHistoryEnabled(metricsHistoryEnabled)
            .setCategory(normalizedCategory)
            .setIgnoreConnectivityCheck(ignoreConnectivityCheck)
}
