package com.kuvaszuptime.kuvasz.models.handlers

import io.micronaut.core.annotation.Introspected

@Introspected
data class GenericWebhookMessage(
    val monitorId: Long,
    val monitorUrn: String,
    val monitorName: String,
    val monitorDetailsUrl: String,
    val timestamp: Long,
    val type: IntegrationEventType,
    val eventDetails: String,
)
