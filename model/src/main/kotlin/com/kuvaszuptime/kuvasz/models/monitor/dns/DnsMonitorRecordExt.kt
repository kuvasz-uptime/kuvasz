package com.kuvaszuptime.kuvasz.models.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.JsonNodeToMatcherListConverter
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import tools.jackson.databind.JsonNode

private val converter = JsonNodeToMatcherListConverter()

fun DnsMonitorRecord.recordMatchersAsList(): List<DnsRecordMatcher> = converter.from(recordMatchers)

fun List<DnsRecordMatcher>.toJsonNode(): JsonNode = converter.to(this)

fun DnsMonitorRecord.assertionRecordTypes(): Set<DnsRecordType> =
    recordMatchersAsList().map { it.recordType }.toSet().ifEmpty { setOf(DnsRecordType.A) }

fun DnsMonitorRecord.driftWatchTypes(): Set<DnsRecordType> =
    if (!driftDetectionEnabled) {
        emptySet()
    } else {
        driftRecordTypes.toSet().ifEmpty { assertionRecordTypes() }
    }

fun DnsMonitorRecord.monitorId() = MonitorID(MonitorType.DNS, name)
fun DnsMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.DNS, id)
