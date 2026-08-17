package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordMatcher
import com.kuvaszuptime.kuvasz.models.monitor.dns.DnsRecordType
import com.kuvaszuptime.kuvasz.models.monitor.dns.recordMatchersAsList
import io.micronaut.core.annotation.Introspected

@Introspected
data class DnsMonitorExportDto(
    val name: String,
    val host: String,
    val resolverHost: String?,
    val resolverPort: Int,
    val transport: DnsTransport,
    val recordMatchers: List<DnsRecordMatcher>,
    val expectedResponseCode: DnsResponseCode,
    val driftDetectionEnabled: Boolean,
    val driftRecordTypes: List<DnsRecordType>,
    val uptimeCheckInterval: Int,
    val timeoutMs: Int,
    val latencyThresholdMs: Int?,
    val failureCountThreshold: Long,
    val enabled: Boolean,
    val integrations: Set<IntegrationID>,
    val metricsHistoryEnabled: Boolean,
    val category: String? = null,
) {
    companion object {
        fun fromMonitorRecord(record: DnsMonitorRecord): DnsMonitorExportDto {
            return DnsMonitorExportDto(
                name = record.name,
                host = record.host,
                resolverHost = record.resolverHost,
                resolverPort = record.resolverPort,
                transport = record.transport,
                recordMatchers = record.recordMatchersAsList(),
                expectedResponseCode = record.expectedResponseCode,
                driftDetectionEnabled = record.driftDetectionEnabled,
                driftRecordTypes = record.driftRecordTypes.toList(),
                uptimeCheckInterval = record.uptimeCheckInterval,
                timeoutMs = record.timeoutMs,
                latencyThresholdMs = record.latencyThresholdMs,
                failureCountThreshold = record.failureCountThreshold,
                enabled = record.enabled,
                integrations = record.integrations.toSet(),
                metricsHistoryEnabled = record.metricsHistoryEnabled,
                category = record.category,
            )
        }
    }
}
