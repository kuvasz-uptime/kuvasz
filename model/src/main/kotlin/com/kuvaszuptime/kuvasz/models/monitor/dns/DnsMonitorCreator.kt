package com.kuvaszuptime.kuvasz.models.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.toNormalizedCategory
import com.kuvaszuptime.kuvasz.validation.ValidDnsRecordMatchers
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Suppress("ComplexInterface")
interface DnsMonitorCreator : DnsResponseCodeMatchers {
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String

    @get:NotBlank(message = MonitorValidationMessages.HOST_NOT_BLANK)
    val host: String

    @get:Pattern(regexp = Validation.NOT_BLANK_REGEX, message = MonitorValidationMessages.RESOLVER_HOST_NOT_BLANK)
    val resolverHost: String?

    @get:NotNull(message = MonitorValidationMessages.RESOLVER_PORT_NOT_NULL)
    @get:Min(Validation.MIN_PORT, message = MonitorValidationMessages.RESOLVER_PORT_MIN)
    @get:Max(Validation.MAX_PORT, message = MonitorValidationMessages.RESOLVER_PORT_MAX)
    val resolverPort: Int

    val transport: DnsTransport

    @get:ValidDnsRecordMatchers
    override val recordMatchers: List<DnsRecordMatcher>?

    override val expectedResponseCode: DnsResponseCode
    val driftDetectionEnabled: Boolean
    val driftRecordTypes: List<DnsRecordType>?

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
    val integrations: List<String>?
    val metricsHistoryEnabled: Boolean

    @get:Size(max = Validation.MAX_CATEGORY_LENGTH, message = MonitorValidationMessages.CATEGORY_MAX_SIZE)
    val category: String?
}

fun DnsMonitorCreator.toMonitorRecord(validatedIntegrations: Set<IntegrationID>): DnsMonitorRecord =
    DnsMonitorRecord()
        .setName(name)
        .setHost(host)
        .setResolverHost(resolverHost)
        .setResolverPort(resolverPort)
        .setTransport(transport)
        .setRecordMatchers(recordMatchers.orEmpty().distinct().toJsonNode())
        .setExpectedResponseCode(expectedResponseCode)
        .setDriftDetectionEnabled(driftDetectionEnabled)
        .setDriftRecordTypes(driftRecordTypes.orEmpty().distinct().toTypedArray())
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setTimeoutMs(timeoutMs)
        .setLatencyThresholdMs(latencyThresholdMs)
        .setFailureCountThreshold(failureCountThreshold)
        .setEnabled(enabled)
        .setIntegrations(validatedIntegrations.toTypedArray())
        .setMetricsHistoryEnabled(metricsHistoryEnabled)
        .setCategory(category.toNormalizedCategory())
