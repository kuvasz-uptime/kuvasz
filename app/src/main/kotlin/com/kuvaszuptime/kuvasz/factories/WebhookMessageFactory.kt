package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.handlers.WebhookMonitorStatus
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import jakarta.inject.Singleton
import java.io.StringWriter
import java.time.Instant

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

    private fun UptimeMonitorEvent.getWebhookStatus(): WebhookMonitorStatus = when (this) {
        is HttpMonitorDownEvent -> WebhookMonitorStatus.HTTP_DOWN
        is HttpMonitorUpEvent -> WebhookMonitorStatus.HTTP_UP
        is PushMonitorDownEvent -> WebhookMonitorStatus.PUSH_DOWN
        is PushMonitorUpEvent -> WebhookMonitorStatus.PUSH_UP
    }

    private fun SSLMonitorEvent.getWebhookStatus(): WebhookMonitorStatus = when (this) {
        is SSLInvalidEvent -> WebhookMonitorStatus.SSL_INVALID
        is SSLValidEvent -> WebhookMonitorStatus.SSL_VALID
        is SSLWillExpireEvent -> WebhookMonitorStatus.SSL_WILL_EXPIRE
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

    fun fromUptimeEvent(event: UptimeMonitorEvent): GenericWebhookMessage =
        GenericWebhookMessage(
            deduplicationKey = event.deduplicationKey,
            monitorId = event.monitor.richMonitorId(),
            monitorName = event.monitor.name,
            timestamp = Instant.now().toEpochMilli(),
            status = event.getWebhookStatus(),
            eventDetails = event.getEventDetails(),
        )

    fun fromSslEvent(event: SSLMonitorEvent): GenericWebhookMessage =
        GenericWebhookMessage(
            deduplicationKey = event.deduplicationKey,
            monitorId = event.monitor.richMonitorId(),
            monitorName = event.monitor.name,
            timestamp = Instant.now().toEpochMilli(),
            status = event.getWebhookStatus(),
            eventDetails = event.getEventDetails(),
        )

    // TODO this is just a PoC
    // validate templates (here or during app bootstrap)
    // handle errors gracefully
    // provide a hydrated, unified input instead the current event that could be documented for templating
    fun fromUptimeEvent(event: UptimeMonitorEvent, literalTemplate: String): String {
        val compiledTemplate = templateEngine.getTemplate(literalTemplate)
        val context = mapOf(
            "event" to event,
        )
        val writer = StringWriter()
        compiledTemplate.evaluate(writer, context)

        return writer.toString()
    }
}
