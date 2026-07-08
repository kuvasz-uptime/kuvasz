package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class PushMonitorImportAdapter(dto: PushMonitorExportDto) : PushMonitorCreator {
    override val name: String = dto.name
    override val heartbeatInterval: Long = dto.heartbeatInterval
    override val gracePeriod: Long = dto.gracePeriod
    override val clientSecret: String = dto.clientSecret
    override val enabled: Boolean = dto.enabled
    override val integrations: List<String> = dto.integrations.map { it.toString() }
    override val failureCountThreshold: Long = dto.failureCountThreshold
}
