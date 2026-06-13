package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Duration

private val objectMapper: ObjectMapper = jacksonObjectMapper()

fun monitorToggleUpdate(enabled: Boolean): ObjectNode =
    objectMapper.createObjectNode().put(MonitorDetailsDto::enabled.name, enabled)

fun String?.asDuration() = this?.let { Duration.parse(it) }
