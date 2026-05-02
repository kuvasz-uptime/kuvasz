package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import io.pebbletemplates.pebble.PebbleEngine
import jakarta.inject.Singleton
import java.io.StringWriter

@Singleton
class WebhookMessageFactory(private val templateEngine: PebbleEngine) {

    companion object {
        private const val CONTEXT_TEMPLATE_KEY = "ctx"
    }

    private fun MonitorRecord.richMonitorId(): MonitorID = when (this) {
        is HttpMonitorRecord -> this.monitorId()
        is PushMonitorRecord -> this.monitorId()
        else -> throw IllegalArgumentException("Invalid monitor type: $this")
    }

    private fun UptimeMonitorEvent.getEventDetails(): String? = when (this) {
        is HttpMonitorDownEvent -> this.toStructuredMessage().error
        is PushMonitorDownEvent -> this.toStructuredMessage().error
        is HttpMonitorUpEvent, is PushMonitorUpEvent -> this.toStructuredMessage().summary
    }

    private fun SSLMonitorEvent.getEventDetails(): String? = when (this) {
        is SSLInvalidEvent -> this.toStructuredMessage().error
        is SSLWillExpireEvent -> this.toStructuredMessage().validUntil
        is SSLValidEvent -> this.toStructuredMessage().summary
    }

    private fun fromUptimeEvent(event: UptimeMonitorEvent): GenericWebhookMessage =
        GenericWebhookMessage(
            monitorId = event.monitor.richMonitorId(),
            monitorName = event.monitor.name,
            timestamp = event.dispatchedAt.toInstant().toEpochMilli(),
            type = event.getIntegrationEventType(),
            eventDetails = event.getEventDetails(),
        )

    private fun fromSslEvent(event: SSLMonitorEvent): GenericWebhookMessage =
        GenericWebhookMessage(
            monitorId = event.monitor.richMonitorId(),
            monitorName = event.monitor.name,
            timestamp = event.dispatchedAt.toInstant().toEpochMilli(),
            type = event.getIntegrationEventType(),
            eventDetails = event.getEventDetails(),
        )

    @Suppress("NotImplementedDeclaration")
    fun fromMonitorEvent(event: MonitorEvent<*>): GenericWebhookMessage = when (event) {
        is UptimeMonitorEvent -> fromUptimeEvent(event)
        is SSLMonitorEvent -> fromSslEvent(event)
        is HttpRedirectEvent -> throw NotImplementedError("Redirect events are not supported in webhooks")
    }

    @Suppress("NotImplementedDeclaration")
    fun fromMonitorEvent(event: MonitorEvent<*>, literalTemplate: String): String {
        val compiledTemplate = templateEngine.getTemplate(literalTemplate)
        val context = when (event) {
            is UptimeMonitorEvent -> fromUptimeEvent(event)
            is SSLMonitorEvent -> fromSslEvent(event)
            is HttpRedirectEvent -> throw NotImplementedError("Redirect events are not supported in webhooks")
        }
        val writer = StringWriter()
        compiledTemplate.evaluate(writer, mapOf(CONTEXT_TEMPLATE_KEY to context))

        return writer.toString()
    }
}

fun UptimeMonitorEvent.getIntegrationEventType(): IntegrationEventType = when (this) {
    is HttpMonitorDownEvent -> IntegrationEventType.HTTP_DOWN
    is HttpMonitorUpEvent -> IntegrationEventType.HTTP_UP
    is PushMonitorDownEvent -> IntegrationEventType.PUSH_DOWN
    is PushMonitorUpEvent -> IntegrationEventType.PUSH_UP
}

fun SSLMonitorEvent.getIntegrationEventType(): IntegrationEventType = when (this) {
    is SSLInvalidEvent -> IntegrationEventType.SSL_INVALID
    is SSLValidEvent -> IntegrationEventType.SSL_VALID
    is SSLWillExpireEvent -> IntegrationEventType.SSL_WILL_EXPIRE
}
