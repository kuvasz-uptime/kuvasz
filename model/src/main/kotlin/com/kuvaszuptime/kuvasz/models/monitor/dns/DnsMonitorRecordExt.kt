package com.kuvaszuptime.kuvasz.models.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.JsonNodeToMatcherListConverter
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import tools.jackson.databind.JsonNode

private val converter = JsonNodeToMatcherListConverter()

fun DnsMonitorRecord.recordMatchersAsList(): List<DnsRecordMatcher> = converter.from(recordMatchers)

fun List<DnsRecordMatcher>.toJsonNode(): JsonNode = converter.to(this)
