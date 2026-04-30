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
import com.kuvaszuptime.kuvasz.models.handlers.WebhookEventType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import jakarta.inject.Singleton
import java.io.StringWriter

@Singleton
class WebhookMessageFactory {

    private val templateEngine = PebbleEngine.Builder().loader(StringLoader()).build()

    private val UptimeMonitorEvent.deduplicationKey: String
        get() = "kuvasz_uptime_${monitor.id}"

    private val SSLMonitorEvent.deduplicationKey: String
        get() = "kuvasz_ssl_${monitor.id}"

    private fun MonitorRecord.richMonitorId(): MonitorID = when (this) {
        is HttpMonitorRecord -> this.monitorId()
        is PushMonitorRecord -> this.monitorId()
        else -> throw IllegalArgumentException("Invalid monitor type: $this")
    }

    private fun UptimeMonitorEvent.getWebhookEventType(): WebhookEventType = when (this) {
        is HttpMonitorDownEvent -> WebhookEventType.HTTP_DOWN
        is HttpMonitorUpEvent -> WebhookEventType.HTTP_UP
        is PushMonitorDownEvent -> WebhookEventType.PUSH_DOWN
        is PushMonitorUpEvent -> WebhookEventType.PUSH_UP
    }

    private fun SSLMonitorEvent.getWebhookEventType(): WebhookEventType = when (this) {
        is SSLInvalidEvent -> WebhookEventType.SSL_INVALID
        is SSLValidEvent -> WebhookEventType.SSL_VALID
        is SSLWillExpireEvent -> WebhookEventType.SSL_WILL_EXPIRE
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
            deduplicationKey = event.deduplicationKey,
            monitorId = event.monitor.richMonitorId(),
            monitorName = event.monitor.name,
            timestamp = event.dispatchedAt.toInstant().toEpochMilli(),
            type = event.getWebhookEventType(),
            eventDetails = event.getEventDetails(),
        )

    private fun fromSslEvent(event: SSLMonitorEvent): GenericWebhookMessage =
        GenericWebhookMessage(
            deduplicationKey = event.deduplicationKey,
            monitorId = event.monitor.richMonitorId(),
            monitorName = event.monitor.name,
            timestamp = event.dispatchedAt.toInstant().toEpochMilli(),
            type = event.getWebhookEventType(),
            eventDetails = event.getEventDetails(),
        )

    @Suppress("NotImplementedDeclaration")
    fun fromMonitorEvent(event: MonitorEvent<*>): GenericWebhookMessage = when (event) {
        is UptimeMonitorEvent -> fromUptimeEvent(event)
        is SSLMonitorEvent -> fromSslEvent(event)
        is HttpRedirectEvent -> throw NotImplementedError("Redirect events are not supported in webhooks")
    }

    // TODO validate templates (here or during app bootstrap)
    @Suppress("NotImplementedDeclaration")
    fun fromMonitorEvent(event: MonitorEvent<*>, literalTemplate: String): String {
        val compiledTemplate = templateEngine.getTemplate(literalTemplate)
        val context = when (event) {
            is UptimeMonitorEvent -> fromUptimeEvent(event)
            is SSLMonitorEvent -> fromSslEvent(event)
            is HttpRedirectEvent -> throw NotImplementedError("Redirect events are not supported in webhooks")
        }
        val writer = StringWriter()
        // TODO move "ctx" to constant
        compiledTemplate.evaluate(writer, mapOf("ctx" to context))

        return writer.toString()
    }
}
