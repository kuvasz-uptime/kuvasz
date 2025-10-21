package com.kuvaszuptime.kuvasz.models.monitor.http

import com.fasterxml.jackson.databind.JsonNode
import com.kuvaszuptime.kuvasz.jooq.JsonNodeToMapConverter
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID

private val converter = JsonNodeToMapConverter()

fun HttpMonitorRecord.requestHeadersAsMap(): Map<String, String> = converter.from(requestHeaders)
fun HttpMonitorRecord.expectedHeadersAsMap(): Map<String, String> = converter.from(expectedHeaders)

fun Map<String, String>.toJsonNode(): JsonNode = converter.to(this)

fun HttpMonitorRecord.monitorId() = MonitorID(MonitorType.HTTP_SSL, name)
fun HttpMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.HTTP_SSL, id)
