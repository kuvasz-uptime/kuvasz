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
import com.kuvaszuptime.kuvasz.models.handlers.AdaptiveCard
import com.kuvaszuptime.kuvasz.models.handlers.CardContainer
import com.kuvaszuptime.kuvasz.models.handlers.CardTextBlock
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsMessage
import com.kuvaszuptime.kuvasz.models.handlers.MsTeamsNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.containerStyle
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton

/**
 * Turns the notifiable events into Adaptive Cards. The summary of the event becomes the title of a
 * severity-colored container, and the remaining parts of its structured message are rendered as subtle text
 * blocks below it. The emphasis comes from the card itself, so the texts never carry any markdown.
 **/
@Singleton
@Requires(bean = MsTeamsNotificationConfig::class)
class MsTeamsCardFactory {

    companion object {
        private const val TITLE_SIZE = "Medium"
        private const val TITLE_WEIGHT = "Bolder"
        private const val DETAIL_SPACING = "Small"

        // Teams renders a line break inside a TextBlock only for a double newline
        private val NEWLINES = Regex("\n+")
    }

    fun fromUptimeEvent(event: UptimeMonitorEvent): MsTeamsMessage =
        event.toStructuredMessage().let {
            buildCard("${event.getEmoji()} ${it.summary}", it.toDetails(), event.getSeverity())
        }

    fun fromSSLEvent(event: SSLMonitorEvent): MsTeamsMessage =
        event.toStructuredMessage().let {
            buildCard("${event.getEmoji()} ${it.summary}", it.toDetails(), event.getSeverity())
        }

    fun fromDnsRecordsChangedEvent(event: DnsRecordsChangedEvent): MsTeamsMessage =
        event.toStructuredMessage().let {
            buildCard("${event.getEmoji()} ${it.summary}", it.toDetails(), MessageSeverity.INFO)
        }

    fun fromMaintenanceEvent(event: MaintenanceWindowEvent): MsTeamsMessage =
        event.toStructuredMessage().let {
            buildCard("${event.getEmoji()} ${it.summary}", it.toDetails(), event.getSeverity())
        }

    fun testMessage(): MsTeamsMessage = buildCard(Messages.integrationTestMessage(), emptyList(), MessageSeverity.INFO)

    private fun buildCard(title: String, details: List<String>, severity: MessageSeverity): MsTeamsMessage {
        val titleBlock = CardContainer(
            items = listOf(
                CardTextBlock(text = title.toCardText(), size = TITLE_SIZE, weight = TITLE_WEIGHT),
            ),
            style = severity.containerStyle,
        )
        val detailBlocks = details.map { detail ->
            CardTextBlock(text = detail.toCardText(), isSubtle = true, spacing = DETAIL_SPACING)
        }

        return MsTeamsMessage.of(AdaptiveCard(body = listOf(titleBlock) + detailBlocks))
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

    private fun String.toCardText(): String = replace(NEWLINES, "\n\n")
}
