package com.kuvaszuptime.kuvasz.models.handlers

import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected

@Introspected
data class GenericWebhookMessage(
    val monitorId: Long,
    val monitorUrn: MonitorID,
    val monitorName: String,
    val timestamp: Long,
    val type: IntegrationEventType,
    val eventDetails: String,
)
