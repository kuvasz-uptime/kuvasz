package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.MonitorValidationMessages
import com.kuvaszuptime.kuvasz.models.dto.Validation
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsResponseCodeMatchers
import com.kuvaszuptime.kuvasz.validation.ValidDnsRecordMatchers
import com.kuvaszuptime.kuvasz.validation.ValidDnsResponseCode
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Introspected
@ValidDnsResponseCode
data class DnsMonitorUpdateDto(
    @param:Schema(description = MonitorDocs.NAME, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.NAME_NOT_BLANK)
    val name: String?,

    @param:Schema(description = DnsMonitorDocs.HOST, required = false, nullable = false)
    @get:NotBlank(message = MonitorValidationMessages.HOST_NOT_BLANK)
    val host: String?,

    @param:Schema(description = DnsMonitorDocs.RESOLVER_HOST, required = false, nullable = true)
    @get:Pattern(regexp = Validation.NOT_BLANK_REGEX, message = MonitorValidationMessages.RESOLVER_HOST_NOT_BLANK)
    val resolverHost: String?,

    @param:Schema(description = DnsMonitorDocs.RESOLVER_PORT, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.RESOLVER_PORT_NOT_NULL)
    @get:Min(Validation.MIN_PORT, message = MonitorValidationMessages.RESOLVER_PORT_MIN)
    @get:Max(Validation.MAX_PORT, message = MonitorValidationMessages.RESOLVER_PORT_MAX)
    val resolverPort: Int?,

    @param:Schema(description = DnsMonitorDocs.TRANSPORT, required = false, nullable = false)
    @get:NotNull
    val transport: DnsTransport?,

    @param:Schema(description = DnsMonitorDocs.RECORD_MATCHERS, required = false, nullable = false)
    @get:NotNull
    @get:ValidDnsRecordMatchers
    override val recordMatchers: List<DnsRecordMatcher>?,

    @param:Schema(description = DnsMonitorDocs.EXPECTED_RESPONSE_CODE, required = false, nullable = false)
    @get:NotNull
    override val expectedResponseCode: DnsResponseCode?,

    @param:Schema(description = DnsMonitorDocs.DRIFT_DETECTION_ENABLED, required = false, nullable = false)
    @get:NotNull
    val driftDetectionEnabled: Boolean?,

    @param:Schema(description = DnsMonitorDocs.DRIFT_RECORD_TYPES, required = false, nullable = false)
    @get:NotNull
    val driftRecordTypes: List<DnsRecordType>?,

    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_NOT_NULL)
    @get:Min(Validation.MIN_UPTIME_CHECK_INTERVAL, message = MonitorValidationMessages.UPTIME_CHECK_INTERVAL_MIN)
    val uptimeCheckInterval: Int?,

    @param:Schema(description = DnsMonitorDocs.TIMEOUT_MS, required = false, nullable = false)
    @get:NotNull(message = MonitorValidationMessages.TIMEOUT_MILLIS_NOT_NULL)
    @get:Min(Validation.MIN_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MIN)
    @get:Max(Validation.MAX_TIMEOUT_MILLIS, message = MonitorValidationMessages.TIMEOUT_MILLIS_MAX)
    val timeoutMs: Int?,

    @param:Schema(description = DnsMonitorDocs.LATENCY_THRESHOLD_MS, required = false, nullable = true)
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
    @param:Schema(description = DnsMonitorDocs.METRICS_HISTORY_ENABLED, required = false, nullable = false)
    val metricsHistoryEnabled: Boolean?,

    @get:Size(max = Validation.MAX_CATEGORY_LENGTH, message = MonitorValidationMessages.CATEGORY_MAX_SIZE)
    @param:Schema(description = MonitorDocs.CATEGORY, required = false, nullable = true)
    val category: String?,
) : DnsResponseCodeMatchers
