package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class PushMonitorCreateDto(
    @param:Schema(description = PushMonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = PushMonitorDocs.HEARTBEAT_INTERVAL, required = true)
    override val heartbeatInterval: Long,
    @param:Schema(description = PushMonitorDocs.GRACE_PERIOD, required = true)
    override val gracePeriod: Long = PushMonitorDefaults.GRACE_PERIOD_SECONDS,
    @param:Schema(description = PushMonitorDocs.ENABLED, defaultValue = PushMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean = PushMonitorDefaults.MONITOR_ENABLED,
    @param:Schema(description = PushMonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(description = PushMonitorDocs.CLIENT_SECRET, required = true)
    override val clientSecret: String,
) : PushMonitorCreator
