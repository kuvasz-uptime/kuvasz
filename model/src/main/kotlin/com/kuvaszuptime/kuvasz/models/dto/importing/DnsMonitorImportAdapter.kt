package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.validation.ValidDnsResponseCode
import io.micronaut.core.annotation.Introspected

@Introspected
@ValidDnsResponseCode
class DnsMonitorImportAdapter(dto: DnsMonitorExportDto) : DnsMonitorCreator {
    override val name: String = dto.name
    override val host: String = dto.host
    override val resolverHost: String? = dto.resolverHost
    override val resolverPort: Int = dto.resolverPort
    override val transport: DnsTransport = dto.transport
    override val recordMatchers: List<DnsRecordMatcher> = dto.recordMatchers
    override val expectedResponseCode: DnsResponseCode = dto.expectedResponseCode
    override val driftDetectionEnabled: Boolean = dto.driftDetectionEnabled
    override val driftRecordTypes: List<DnsRecordType> = dto.driftRecordTypes
    override val uptimeCheckInterval: Int = dto.uptimeCheckInterval
    override val timeoutMs: Int = dto.timeoutMs
    override val latencyThresholdMs: Int? = dto.latencyThresholdMs
    override val failureCountThreshold: Long = dto.failureCountThreshold
    override val enabled: Boolean = dto.enabled
    override val integrations: List<String> = dto.integrations.map { it.toString() }
    override val metricsHistoryEnabled: Boolean = dto.metricsHistoryEnabled
    override val category: String? = dto.category
}
