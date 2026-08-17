package com.kuvaszuptime.kuvasz.config

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorDefaults
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.core.bind.annotation.Bindable

@EachProperty(DnsMonitorConfig.CONFIG_PREFIX, list = true)
@Introspected
@Suppress("ComplexInterface")
interface DnsMonitorConfig : DnsMonitorCreator, MonitorConfig {

    companion object {
        const val CONFIG_PREFIX = "dns-monitors"
    }

    override val name: String
    override val host: String
    override val uptimeCheckInterval: Int

    @get:Nullable
    override val resolverHost: String?

    @get:Bindable(defaultValue = DnsMonitorDefaults.RESOLVER_PORT.toString())
    override val resolverPort: Int

    @get:Bindable(defaultValue = DnsMonitorDefaults.TRANSPORT)
    override val transport: DnsTransport

    @get:Nullable
    override val recordMatchers: List<DnsRecordMatcher>?

    @get:Bindable(defaultValue = DnsMonitorDefaults.EXPECTED_RESPONSE_CODE)
    override val expectedResponseCode: DnsResponseCode

    @get:Bindable(defaultValue = DnsMonitorDefaults.DRIFT_DETECTION_ENABLED.toString())
    override val driftDetectionEnabled: Boolean

    @get:Nullable
    override val driftRecordTypes: List<DnsRecordType>?

    @get:Bindable(defaultValue = DnsMonitorDefaults.TIMEOUT_MS.toString())
    override val timeoutMs: Int

    @get:Nullable
    override val latencyThresholdMs: Int?

    @get:Bindable(defaultValue = DnsMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString())
    override val failureCountThreshold: Long

    @get:Bindable(defaultValue = DnsMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean

    override val integrations: List<String>?

    @get:Bindable(defaultValue = DnsMonitorDefaults.METRICS_HISTORY_ENABLED.toString())
    override val metricsHistoryEnabled: Boolean

    override val category: String?
}
