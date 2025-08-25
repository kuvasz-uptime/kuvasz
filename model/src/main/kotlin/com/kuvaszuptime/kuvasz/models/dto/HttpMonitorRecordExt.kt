package com.kuvaszuptime.kuvasz.models.dto

import com.fasterxml.jackson.databind.JsonNode
import com.kuvaszuptime.kuvasz.jooq.JsonNodeToMapConverter
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord

private val converter = JsonNodeToMapConverter()

fun HttpMonitorRecord.requestHeadersAsMap(): Map<String, String> = converter.from(requestHeaders)
fun HttpMonitorRecord.expectedHeadersAsMap(): Map<String, String> = converter.from(expectedHeaders)

fun Map<String, String>.toJsonNode(): JsonNode = converter.to(this)
