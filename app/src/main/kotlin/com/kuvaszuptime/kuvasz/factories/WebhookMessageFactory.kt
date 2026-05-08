package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.handlers.toIntegrationEventType
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.PlainTextMessageFormatter
import com.kuvaszuptime.kuvasz.models.handlers.GenericWebhookMessage
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.relativeDetailsUrl
import io.pebbletemplates.pebble.PebbleEngine
import jakarta.inject.Singleton
import java.io.StringWriter

@Singleton
class WebhookMessageFactory(private val templateEngine: PebbleEngine) {

    companion object {
        private const val CONTEXT_TEMPLATE_KEY = "ctx"
    }
    
    private fun MonitorRecord.urn(): MonitorID = when (this) {
        is HttpMonitorRecord -> this.monitorId()
        is PushMonitorRecord -> this.monitorId()
        else -> throw IllegalArgumentException("Invalid monitor type: $this")
    }

    fun fromMonitorEvent(event: MonitorEvent<out MonitorRecord>): GenericWebhookMessage =
        GenericWebhookMessage(
            monitorId = event.monitor.id,
            monitorUrn = event.monitor.urn().toString(),
            monitorName = event.monitor.name,
            monitorDetailsUrl = event.monitor.relativeDetailsUrl,
            timestamp = event.dispatchedAt.toInstant().toEpochMilli(),
            type = event.toIntegrationEventType(),
            eventDetails = event.toFormattedMessage(),
        )

    fun fromMonitorEvent(event: MonitorEvent<*>, literalTemplate: String): String {
        val compiledTemplate = templateEngine.getTemplate(literalTemplate)
        val writer = StringWriter()
        compiledTemplate.evaluate(writer, mapOf(CONTEXT_TEMPLATE_KEY to fromMonitorEvent(event)))

        return writer.toString()
    }

    @Suppress("NotImplementedDeclaration")
    private fun MonitorEvent<*>.toFormattedMessage() = when (this) {
        is UptimeMonitorEvent -> PlainTextMessageFormatter.toFormattedMessage(this)
        is SSLMonitorEvent -> PlainTextMessageFormatter.toFormattedMessage(this)
        is HttpRedirectEvent -> throw NotImplementedError("Redirect events are not supported in webhooks")
    }
}
