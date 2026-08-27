package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.MessageSeverity
import com.kuvaszuptime.kuvasz.models.events.formatters.MsTeamsTextFormatter
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
 * Turns the notifiable events into Adaptive Cards. The message lines are the very same ones the other
 * chat integrations send, only the rendering differs: the first line becomes the title of a
 * severity-colored container, the rest are rendered as subtle text blocks below it.
 **/
@Singleton
@Requires(bean = MsTeamsNotificationConfig::class)
class MsTeamsCardFactory {

    companion object {
        private const val TITLE_SIZE = "Medium"
        private const val DETAIL_SPACING = "Small"

        // Teams renders a line break inside a TextBlock only for a double newline
        private val NEWLINES = Regex("\n+")
    }

    fun fromUptimeEvent(event: UptimeMonitorEvent): MsTeamsMessage =
        buildMessage(MsTeamsTextFormatter.toMessageParts(event), event.getSeverity())

    fun fromSSLEvent(event: SSLMonitorEvent): MsTeamsMessage =
        buildMessage(MsTeamsTextFormatter.toMessageParts(event), event.getSeverity())

    fun fromDnsRecordsChangedEvent(event: DnsRecordsChangedEvent): MsTeamsMessage =
        buildMessage(MsTeamsTextFormatter.toMessageParts(event), MessageSeverity.INFO)

    fun fromMaintenanceEvent(event: MaintenanceWindowEvent): MsTeamsMessage =
        buildMessage(MsTeamsTextFormatter.toMessageParts(event), event.getSeverity())

    fun testMessage(): MsTeamsMessage =
        buildMessage(listOf(Messages.integrationTestMessage()), MessageSeverity.INFO)

    private fun buildMessage(parts: List<String>, severity: MessageSeverity): MsTeamsMessage {
        val title = CardContainer(
            items = listOf(CardTextBlock(text = parts.first().toCardText(), size = TITLE_SIZE)),
            style = severity.containerStyle,
        )
        val details = parts.drop(1).map { part ->
            CardTextBlock(text = part.toCardText(), isSubtle = true, spacing = DETAIL_SPACING)
        }

        return MsTeamsMessage.of(AdaptiveCard(body = listOf(title) + details))
    }

    private fun String.toCardText(): String = replace(NEWLINES, "\n\n")
}
