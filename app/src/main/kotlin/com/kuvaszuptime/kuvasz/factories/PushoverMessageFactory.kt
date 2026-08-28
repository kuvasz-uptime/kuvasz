package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.StructuredDnsMonitorDownMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredDnsMonitorUpMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredDnsRecordsChangedMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredHttpMonitorUpMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredIcmpMonitorDownMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredIcmpMonitorUpMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredMaintenanceEndMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredMaintenanceMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredMaintenanceStartMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredMonitorDownMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredMonitorMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredPushMonitorUpMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredSSLInvalidMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredSSLMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredSSLValidMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredSSLWillExpireMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredTcpMonitorDownMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredTcpMonitorUpMessage
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.MessageSeverity
import com.kuvaszuptime.kuvasz.models.events.formatters.getEmoji
import com.kuvaszuptime.kuvasz.models.events.formatters.toSeverity
import com.kuvaszuptime.kuvasz.models.handlers.PushoverMessage
import com.kuvaszuptime.kuvasz.models.handlers.PushoverNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.PushoverPriority
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Turns the notifiable events into Pushover notifications. The summary of the event becomes the title, and the
 * remaining parts of its structured message become the body, without any markup. The severity is mapped onto the
 * priority of the notification, which decides whether it's allowed to break through the quiet hours of the
 * recipient.
 **/
@Singleton
@Requires(bean = PushoverNotificationConfig::class)
class PushoverMessageFactory {

    fun fromUptimeEvent(event: UptimeMonitorEvent): PushoverMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), event.toSeverity())
        }

    fun fromSSLEvent(event: SSLMonitorEvent): PushoverMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), event.toSeverity())
        }

    fun fromDnsRecordsChangedEvent(event: DnsRecordsChangedEvent): PushoverMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), MessageSeverity.INFO)
        }

    fun fromMaintenanceEvent(event: MaintenanceWindowEvent): PushoverMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), event.toSeverity())
        }

    fun testMessage(): PushoverMessage =
        buildMessage(Messages.integrationTestMessage(), emptyList(), MessageSeverity.INFO)

    private fun buildMessage(title: String, details: List<String>, severity: MessageSeverity): PushoverMessage =
        PushoverMessage(
            title = title.truncatedTo(TITLE_MAX_LENGTH),
            // Pushover rejects a payload without a message, so an event without any detail repeats its title there
            message = details.joinToString("\n").ifBlank { title }.truncatedTo(MESSAGE_MAX_LENGTH),
            priority = severity.toPushoverPriority(),
        )

    private fun MessageSeverity.toPushoverPriority(): PushoverPriority = when (this) {
        MessageSeverity.CRITICAL -> PushoverPriority.HIGH
        MessageSeverity.WARNING, MessageSeverity.OK, MessageSeverity.INFO -> PushoverPriority.NORMAL
    }

    // Pushover answers with a 4xx instead of trimming, and a long enough monitor URL fits into a summary easily
    private fun String.truncatedTo(maxLength: Int): String =
        if (length <= maxLength) this else take(maxLength - 1).trimEnd() + "…"

    private fun StructuredMonitorMessage.toDetails(): List<String> = when (this) {
        is StructuredHttpMonitorUpMessage -> listOfNotNull(latency, previousDownTime)
        is StructuredPushMonitorUpMessage -> listOfNotNull(previousDownTime)
        is StructuredMonitorDownMessage -> listOfNotNull(previousUpTime)
        is StructuredIcmpMonitorUpMessage -> listOfNotNull(latency, packetLoss, previousDownTime)
        is StructuredIcmpMonitorDownMessage -> listOfNotNull(packetLoss, previousUpTime)
        is StructuredTcpMonitorUpMessage -> listOfNotNull(latency, previousDownTime)
        is StructuredTcpMonitorDownMessage -> listOfNotNull(previousUpTime)
        is StructuredDnsMonitorUpMessage -> listOfNotNull(latency, previousDownTime)
        is StructuredDnsMonitorDownMessage -> listOfNotNull(previousUpTime)
    }

    private fun StructuredSSLMessage.toDetails(): List<String> = when (this) {
        is StructuredSSLValidMessage -> listOfNotNull(previousInvalidEvent)
        is StructuredSSLInvalidMessage -> listOfNotNull(error, previousValidEvent)
        is StructuredSSLWillExpireMessage -> listOf(validUntil)
    }

    private fun StructuredMaintenanceMessage.toDetails(): List<String> = when (this) {
        is StructuredMaintenanceStartMessage -> listOfNotNull(description)
        is StructuredMaintenanceEndMessage -> emptyList()
    }

    private fun StructuredDnsRecordsChangedMessage.toDetails(): List<String> =
        listOfNotNull(details.takeIf { it.isNotBlank() })

    companion object {
        private const val TITLE_MAX_LENGTH = 250
        private const val MESSAGE_MAX_LENGTH = 1024
    }
}
