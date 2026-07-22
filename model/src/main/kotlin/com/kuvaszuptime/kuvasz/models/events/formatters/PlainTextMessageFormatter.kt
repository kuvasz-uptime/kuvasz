package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent

object PlainTextMessageFormatter : TextMessageFormatter {

    override fun toFormattedMessage(event: UptimeMonitorEvent): String {
        val messageParts: List<String> = when (event) {
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
        }

        return messageParts.assemble()
    }

    private fun HttpMonitorUpEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, latency, previousDownTime) }

    private fun HttpMonitorDownEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, error, previousUpTime) }

    private fun PushMonitorDownEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, error, previousUpTime) }

    private fun PushMonitorUpEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, previousDownTime) }

    private fun IcmpMonitorUpEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, latency, packetLoss, previousDownTime) }

    private fun IcmpMonitorDownEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, error, packetLoss, previousUpTime) }

    private fun TcpMonitorUpEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, latency, previousDownTime) }

    private fun TcpMonitorDownEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, error, previousUpTime) }

    private fun DnsMonitorUpEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, latency, previousDownTime) }

    private fun DnsMonitorDownEvent.toParts() =
        toStructuredMessage().run { listOfNotNull(summary, error, previousUpTime) }

    fun toFormattedMessage(event: DnsRecordsChangedEvent): String =
        event.toStructuredMessage().run { listOf(summary, details).assemble() }

    override fun toFormattedMessage(event: SSLMonitorEvent): String {
        val messageParts: List<String> = when (event) {
            is SSLValidEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.previousInvalidEvent
                )
            }

            is SSLWillExpireEvent -> event.toStructuredMessage().let { details ->
                listOf(
                    details.summary,
                    details.validUntil
                )
            }

            is SSLInvalidEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.error,
                    details.previousValidEvent
                )
            }
        }

        return messageParts.assemble()
    }

    override fun toFormattedMessage(event: MaintenanceWindowEvent): String {
        val window = event.window
        return when (event) {
            is MaintenanceWindowStartEvent -> listOfNotNull(
                Messages.maintenanceWindowStarted(window.name),
                window.description?.takeIf { it.isNotBlank() },
            )

            is MaintenanceWindowEndEvent -> listOf(Messages.maintenanceWindowEnded(window.name))
        }.assemble()
    }

    private fun List<String>.assemble(): String = joinToString("\n")
}
