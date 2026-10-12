package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DockerMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteCreatedEvent
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteEvent
import com.kuvaszuptime.kuvasz.models.events.IncidentNoteUpdatedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.StructuredMaintenanceEndMessage
import com.kuvaszuptime.kuvasz.models.events.StructuredMaintenanceStartMessage
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.sanitizeAsDetails
import com.kuvaszuptime.kuvasz.models.label

abstract class RichTextMessageFormatter : TextMessageFormatter {
    abstract fun bold(input: String): String

    abstract fun italic(input: String): String

    /**
     * Escapes a text that comes from the users (e.g. the name of a monitor), or from an external source (e.g. the
     * records of a DNS name), so that it's shown as it is, instead of being interpreted as markup
     */
    abstract fun escape(input: String): String

    override fun toFormattedMessage(event: UptimeMonitorEvent): String = when (event) {
        is HttpMonitorUpEvent -> event.toParts()
        is HttpMonitorDownEvent -> event.toParts()
        is PushMonitorDownEvent -> event.toParts()
        is PushMonitorUpEvent -> event.toParts()
        is IcmpMonitorUpEvent -> event.toParts()
        is IcmpMonitorDownEvent -> event.toParts()
        is TcpMonitorUpEvent -> event.toParts()
        is TcpMonitorDownEvent -> event.toParts()
        is DnsMonitorUpEvent -> event.toParts()
        is DnsMonitorDownEvent -> event.toParts()
        is DockerMonitorUpEvent -> event.toParts()
        is DockerMonitorDownEvent -> event.toParts()
    }.assemble()

    private fun HttpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(
            getEmoji() + " " + bold(escape(details.summary)),
            italic(details.latency),
            details.previousDownTime,
        )
    }

    private fun HttpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.previousUpTime)
    }

    private fun PushMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.previousUpTime)
    }

    private fun PushMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.previousDownTime)
    }

    private fun IcmpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(
            getEmoji() + " " + bold(escape(details.summary)),
            details.latency?.let { italic(it) },
            details.packetLoss,
            details.previousDownTime,
        )
    }

    private fun IcmpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.packetLoss, details.previousUpTime)
    }

    private fun TcpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(
            getEmoji() + " " + bold(escape(details.summary)),
            details.latency?.let { italic(it) },
            details.previousDownTime,
        )
    }

    private fun TcpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.previousUpTime)
    }

    private fun DockerMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.previousDownTime)
    }

    private fun DockerMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.previousUpTime)
    }

    private fun DnsMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(
            getEmoji() + " " + bold(escape(details.summary)),
            details.latency?.let { italic(it) },
            details.previousDownTime,
        )
    }

    private fun DnsMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(escape(details.summary)), details.previousUpTime)
    }

    fun toFormattedMessage(event: DnsRecordsChangedEvent): String = event.toStructuredMessage().let { details ->
        listOfNotNull(
            event.getEmoji() + " " + bold(escape(details.summary)),
            details.details.takeIf { it.isNotBlank() }?.let { escape(it) },
        )
    }.assemble()

    fun toFormattedMessage(event: DockerContainerRestartedEvent): String = event.toStructuredMessage().let { details ->
        listOf(event.getEmoji() + " " + bold(escape(details.summary)), details.details)
    }.assemble()

    fun toFormattedMessage(event: IncidentNoteEvent): String = with(event.note) {
        val headline = when (event) {
            is IncidentNoteCreatedEvent -> Messages.incidentNoteAdded()
            is IncidentNoteUpdatedEvent -> Messages.incidentNoteUpdated()
        }
        listOfNotNull(
            Emoji.MEMO + " " + bold(headline) + " · " + italic(status.label),
            timestamp?.let { noteTime -> italic(Messages.incidentNoteTime(noteTime)) },
            escape(content.sanitizeAsDetails()),
        )
    }.assemble()

    override fun toFormattedMessage(event: SSLMonitorEvent): String = when (event) {
        is SSLValidEvent -> event.toStructuredMessage().let { details ->
            listOfNotNull(
                event.getEmoji() + " " + bold(escape(details.summary)),
                details.previousInvalidEvent
            )
        }

        is SSLWillExpireEvent -> event.toStructuredMessage().let { details ->
            listOf(
                event.getEmoji() + " " + bold(escape(details.summary)),
                italic(details.validUntil)
            )
        }

        is SSLInvalidEvent -> event.toStructuredMessage().let { details ->
            listOfNotNull(
                event.getEmoji() + " " + bold(escape(details.summary)),
                italic(escape(details.error)),
                details.previousValidEvent
            )
        }
    }.assemble()

    override fun toFormattedMessage(event: MaintenanceWindowEvent): String =
        event.toStructuredMessage().let { details ->
            val headline = event.getEmoji() + " " + bold(escape(details.summary))
            when (details) {
                is StructuredMaintenanceStartMessage ->
                    listOfNotNull(headline, details.description?.let { italic(escape(it)) })

                is StructuredMaintenanceEndMessage -> listOf(headline)
            }
        }.assemble()

    private fun List<String>.assemble(): String = joinToString("\n")
}
