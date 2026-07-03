package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import io.micronaut.core.annotation.Introspected

@Introspected
class PushMonitorImportAdapter(private val dto: PushMonitorExportDto) : PushMonitorCreator {
    override val name: String get() = dto.name
    override val heartbeatInterval: Long get() = dto.heartbeatInterval
    override val gracePeriod: Long get() = dto.gracePeriod
    override val clientSecret: String get() = dto.clientSecret
    override val enabled: Boolean get() = dto.enabled
    override val integrations: List<String>? get() = dto.integrations.map { it.toString() }
    override val failureCountThreshold: Long get() = dto.failureCountThreshold
}
