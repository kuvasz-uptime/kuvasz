package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsResponseCodeMatchers
import com.kuvaszuptime.kuvasz.validation.ValidDnsResponseCode
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
@ValidDnsResponseCode
data class DnsMonitorCreateDto(
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = DnsMonitorDocs.HOST, required = true)
    override val host: String,
    @param:Schema(description = MonitorDocs.UPTIME_CHECK_INTERVAL, required = true)
    override val uptimeCheckInterval: Int,
    @param:Schema(description = DnsMonitorDocs.RESOLVER_HOST, required = false, nullable = true)
    override val resolverHost: String? = null,
    @param:Schema(
        description = DnsMonitorDocs.RESOLVER_PORT,
        required = false,
        defaultValue = DnsMonitorDefaults.RESOLVER_PORT.toString()
    )
    override val resolverPort: Int = DnsMonitorDefaults.RESOLVER_PORT,
    @param:Schema(description = DnsMonitorDocs.TRANSPORT, required = false, defaultValue = DnsMonitorDefaults.TRANSPORT)
    override val transport: DnsTransport = DnsTransport.UDP,
    @param:Schema(description = DnsMonitorDocs.RECORD_MATCHERS, required = false)
    override val recordMatchers: List<DnsRecordMatcher>? = emptyList(),
    @param:Schema(
        description = DnsMonitorDocs.EXPECTED_RESPONSE_CODE,
        required = false,
        defaultValue = DnsMonitorDefaults.EXPECTED_RESPONSE_CODE
    )
    override val expectedResponseCode: DnsResponseCode = DnsResponseCode.NOERROR,
    @param:Schema(
        description = DnsMonitorDocs.DRIFT_DETECTION_ENABLED,
        required = false,
        defaultValue = DnsMonitorDefaults.DRIFT_DETECTION_ENABLED.toString()
    )
    override val driftDetectionEnabled: Boolean = DnsMonitorDefaults.DRIFT_DETECTION_ENABLED,
    @param:Schema(description = DnsMonitorDocs.DRIFT_RECORD_TYPES, required = false)
    override val driftRecordTypes: List<DnsRecordType>? = emptyList(),
    @param:Schema(
        description = DnsMonitorDocs.TIMEOUT_MS,
        required = false,
        defaultValue = DnsMonitorDefaults.TIMEOUT_MS.toString()
    )
    override val timeoutMs: Int = DnsMonitorDefaults.TIMEOUT_MS,
    @param:Schema(description = DnsMonitorDocs.LATENCY_THRESHOLD_MS, required = false, nullable = true)
    override val latencyThresholdMs: Int? = null,
    @param:Schema(
        description = MonitorDocs.FAILURE_COUNT_THRESHOLD,
        required = false,
        defaultValue = DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString()
    )
    override val failureCountThreshold: Long = DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD,
    @param:Schema(
        description = MonitorDocs.ENABLED,
        defaultValue = DnsMonitorDefaults.MONITOR_ENABLED.toString()
    )
    override val enabled: Boolean = DnsMonitorDefaults.MONITOR_ENABLED,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(
        description = DnsMonitorDocs.METRICS_HISTORY_ENABLED,
        required = false,
        defaultValue = DnsMonitorDefaults.METRICS_HISTORY_ENABLED.toString()
    )
    override val metricsHistoryEnabled: Boolean = DnsMonitorDefaults.METRICS_HISTORY_ENABLED,
) : DnsMonitorCreator, DnsResponseCodeMatchers
