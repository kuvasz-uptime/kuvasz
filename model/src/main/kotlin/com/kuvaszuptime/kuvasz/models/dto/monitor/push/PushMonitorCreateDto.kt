package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import com.kuvaszuptime.kuvasz.models.dto.monitor.MonitorDocs
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class PushMonitorCreateDto(
    @param:Schema(description = MonitorDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = PushMonitorDocs.HEARTBEAT_INTERVAL, required = true)
    override val heartbeatInterval: Long,
    @param:Schema(description = PushMonitorDocs.GRACE_PERIOD, required = true)
    override val gracePeriod: Long = PushMonitorDefaults.GRACE_PERIOD_SECONDS,
    @param:Schema(description = MonitorDocs.ENABLED, defaultValue = PushMonitorDefaults.MONITOR_ENABLED.toString())
    override val enabled: Boolean = PushMonitorDefaults.MONITOR_ENABLED,
    @param:Schema(description = MonitorDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
    @param:Schema(description = PushMonitorDocs.CLIENT_SECRET, required = true)
    override val clientSecret: String,
    @param:Schema(
        description = MonitorDocs.FAILURE_COUNT_THRESHOLD,
        required = false,
        defaultValue = PushMonitorDefaults.FAILURE_COUNT_THRESHOLD.toString()
    )
    override val failureCountThreshold: Long = PushMonitorDefaults.FAILURE_COUNT_THRESHOLD,
) : PushMonitorCreator
