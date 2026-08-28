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
import com.kuvaszuptime.kuvasz.models.events.formatters.getSeverity
import com.kuvaszuptime.kuvasz.models.handlers.AppriseMessage
import com.kuvaszuptime.kuvasz.models.handlers.AppriseNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.AppriseType
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Turns the notifiable events into Apprise notifications. The summary of the event becomes the title, and the
 * remaining parts of its structured message become the body. The severity is mapped onto the notification type
 * Apprise translates to the target services, so the texts never carry any markup.
 **/
@Singleton
@Requires(bean = AppriseNotificationConfig::class)
class AppriseMessageFactory {

    fun fromUptimeEvent(event: UptimeMonitorEvent): AppriseMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), event.getSeverity())
        }

    fun fromSSLEvent(event: SSLMonitorEvent): AppriseMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), event.getSeverity())
        }

    fun fromDnsRecordsChangedEvent(event: DnsRecordsChangedEvent): AppriseMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), MessageSeverity.INFO)
        }

    fun fromMaintenanceEvent(event: MaintenanceWindowEvent): AppriseMessage =
        event.toStructuredMessage().let {
            buildMessage("${event.getEmoji()} ${it.summary}", it.toDetails(), event.getSeverity())
        }

    fun testMessage(): AppriseMessage =
        buildMessage(Messages.integrationTestMessage(), emptyList(), MessageSeverity.INFO)

    private fun buildMessage(title: String, details: List<String>, severity: MessageSeverity): AppriseMessage =
        AppriseMessage(
            title = title,
            // Apprise rejects a payload without a body, so an event without any detail repeats its title there
            body = details.joinToString("\n").ifBlank { title },
            type = severity.toAppriseType(),
        )

    private fun MessageSeverity.toAppriseType(): AppriseType = when (this) {
        MessageSeverity.CRITICAL -> AppriseType.FAILURE
        MessageSeverity.WARNING -> AppriseType.WARNING
        MessageSeverity.OK -> AppriseType.SUCCESS
        MessageSeverity.INFO -> AppriseType.INFO
    }

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
}
