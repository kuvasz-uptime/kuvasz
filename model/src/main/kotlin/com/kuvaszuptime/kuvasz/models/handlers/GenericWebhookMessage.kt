package com.kuvaszuptime.kuvasz.models.handlers

import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected

@Introspected
data class GenericWebhookMessage(
    val deduplicationKey: String,
    val monitorId: MonitorID,
    val monitorName: String,
    val timestamp: Long,
    val status: WebhookMonitorStatus,
    val eventDetails: String?,
)

enum class WebhookMonitorStatus {
    HTTP_UP,
    HTTP_DOWN,
    PUSH_UP,
    PUSH_DOWN,
    SSL_VALID,
    SSL_INVALID,
    SSL_WILL_EXPIRE,
}
