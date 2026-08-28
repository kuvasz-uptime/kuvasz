package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
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

abstract class RichTextMessageFormatter : TextMessageFormatter {
    abstract fun bold(input: String): String

    abstract fun italic(input: String): String

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
    }.assemble()

    private fun HttpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(details.summary), italic(details.latency), details.previousDownTime)
    }

    private fun HttpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(details.summary), details.previousUpTime)
    }

    private fun PushMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(details.summary), details.previousUpTime)
    }

    private fun PushMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(details.summary), details.previousDownTime)
    }

    private fun IcmpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(
            getEmoji() + " " + bold(details.summary),
            details.latency?.let { italic(it) },
            details.packetLoss,
            details.previousDownTime,
        )
    }

    private fun IcmpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(details.summary), details.packetLoss, details.previousUpTime)
    }

    private fun TcpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(
            getEmoji() + " " + bold(details.summary),
            details.latency?.let { italic(it) },
            details.previousDownTime,
        )
    }

    private fun TcpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(details.summary), details.previousUpTime)
    }

    private fun DnsMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(
            getEmoji() + " " + bold(details.summary),
            details.latency?.let { italic(it) },
            details.previousDownTime,
        )
    }

    private fun DnsMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + bold(details.summary), details.previousUpTime)
    }

    fun toFormattedMessage(event: DnsRecordsChangedEvent): String = event.toStructuredMessage().let { details ->
        listOfNotNull(
            event.getEmoji() + " " + bold(details.summary),
            details.details.takeIf { it.isNotBlank() },
        )
    }.assemble()

    override fun toFormattedMessage(event: SSLMonitorEvent): String = when (event) {
        is SSLValidEvent -> event.toStructuredMessage().let { details ->
            listOfNotNull(
                event.getEmoji() + " " + bold(details.summary),
                details.previousInvalidEvent
            )
        }

        is SSLWillExpireEvent -> event.toStructuredMessage().let { details ->
            listOf(
                event.getEmoji() + " " + bold(details.summary),
                italic(details.validUntil)
            )
        }

        is SSLInvalidEvent -> event.toStructuredMessage().let { details ->
            listOfNotNull(
                event.getEmoji() + " " + bold(details.summary),
                italic(details.error),
                details.previousValidEvent
            )
        }
    }.assemble()

    override fun toFormattedMessage(event: MaintenanceWindowEvent): String =
        event.toStructuredMessage().let { details ->
            val headline = event.getEmoji() + " " + bold(details.summary)
            when (details) {
                is StructuredMaintenanceStartMessage ->
                    listOfNotNull(headline, details.description?.let { italic(it) })

                is StructuredMaintenanceEndMessage -> listOf(headline)
            }
        }.assemble()

    private fun List<String>.assemble(): String = joinToString("\n")
}
