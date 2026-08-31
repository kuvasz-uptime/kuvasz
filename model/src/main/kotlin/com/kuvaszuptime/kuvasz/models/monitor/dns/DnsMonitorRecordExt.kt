package com.kuvaszuptime.kuvasz.models.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.JsonNodeToMatcherListConverter
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorIDWithName
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID
import tools.jackson.databind.JsonNode

private val converter = JsonNodeToMatcherListConverter()

fun DnsMonitorRecord.recordMatchersAsList(): List<DnsRecordMatcher> = converter.from(recordMatchers)

fun List<DnsRecordMatcher>.toJsonNode(): JsonNode = converter.to(this)

fun List<DnsRecordMatcher>.assertionRecordTypes(): Set<DnsRecordType> =
    map { it.recordType }.toSet().ifEmpty { setOf(DnsRecordType.A) }

private fun DnsMonitorRecord.driftRecordTypesOrEmpty(): List<DnsRecordType> = driftRecordTypes.orEmpty().toList()

fun DnsMonitorRecord.driftWatchTypes(default: Set<DnsRecordType>): Set<DnsRecordType> =
    if (!driftDetectionEnabled) {
        emptySet()
    } else {
        driftRecordTypesOrEmpty().toSet().ifEmpty { default }
    }

fun DnsMonitorRecord.deduplicated(): DnsMonitorRecord =
    setRecordMatchers(recordMatchersAsList().distinct().toJsonNode())
        .setDriftRecordTypes(driftRecordTypesOrEmpty().distinct().toTypedArray())

fun DnsMonitorRecord.monitorId() = MonitorID(MonitorType.DNS, name)
fun DnsMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.DNS, id)
fun DnsMonitorRecord.idWithName() = MonitorIDWithName(MonitorType.DNS, id, name)
