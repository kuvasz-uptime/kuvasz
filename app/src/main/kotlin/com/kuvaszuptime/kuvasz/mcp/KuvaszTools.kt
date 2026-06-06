package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorException
import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDetailsDto
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Duration

abstract class KuvaszTools(private val objectMapper: ObjectMapper) {

    protected fun enabledPatch(enabled: Boolean): ObjectNode =
        objectMapper.createObjectNode().put(MonitorDetailsDto::enabled.name, enabled)
}

fun String?.asDuration() = this?.let { Duration.parse(it) }

fun AppConfig.checkMonitorMutability(type: MonitorType) = when (type) {
    MonitorType.HTTP_SSL -> isHttpMonitorExternalWriteDisabled()
    MonitorType.PUSH -> isPushMonitorExternalWriteDisabled()
    MonitorType.ICMP -> isIcmpMonitorExternalWriteDisabled()
}.let { isImmutable ->
    if (isImmutable) {
        throw ReadOnlyMonitorException()
    }
}
