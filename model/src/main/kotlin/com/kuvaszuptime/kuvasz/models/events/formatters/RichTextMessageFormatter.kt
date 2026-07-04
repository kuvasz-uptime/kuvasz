package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.i18n.Messages
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
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent

abstract class RichTextMessageFormatter : TextMessageFormatter {
    abstract fun bold(input: String): String

    abstract fun italic(input: String): String

    override fun toFormattedMessage(event: UptimeMonitorEvent): String {
        val messageParts: List<String> = when (event) {
            is HttpMonitorUpEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    italic(details.latency),
                    details.previousDownTime
                )
            }

            is HttpMonitorDownEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    details.previousUpTime
                )
            }

            is PushMonitorDownEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    details.previousUpTime
                )
            }

            is PushMonitorUpEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    details.previousDownTime
                )
            }

            is IcmpMonitorUpEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    details.latency?.let { italic(it) },
                    details.packetLoss,
                    details.previousDownTime
                )
            }

            is IcmpMonitorDownEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    details.packetLoss,
                    details.previousUpTime
                )
            }
        }

        return messageParts.assemble()
    }

    override fun toFormattedMessage(event: SSLMonitorEvent): String {
        val messageParts: List<String> = when (event) {
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
        }

        return messageParts.assemble()
    }

    override fun toFormattedMessage(event: MaintenanceWindowEvent): String {
        val window = event.window
        val summary = when (event) {
            is MaintenanceWindowStartEvent -> Messages.maintenanceWindowStarted(window.name)
            is MaintenanceWindowEndEvent -> Messages.maintenanceWindowEnded(window.name)
        }
        return listOfNotNull(
            event.getEmoji() + " " + bold(summary),
            window.description?.takeIf { it.isNotBlank() }?.let { italic(it) },
        ).assemble()
    }

    private fun List<String>.assemble(): String = joinToString("\n")
}
