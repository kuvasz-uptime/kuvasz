package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
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

object LogMessageFormatter : TextMessageFormatter {

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
        }

        return messageParts.assemble()
    }

    private fun HttpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.latency, details.previousDownTime)
    }

    private fun HttpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.error, details.previousUpTime)
    }

    private fun PushMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.error, details.previousUpTime)
    }

    private fun PushMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.previousDownTime)
    }

    private fun IcmpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.latency, details.packetLoss, details.previousDownTime)
    }

    private fun IcmpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.error, details.packetLoss, details.previousUpTime)
    }

    private fun TcpMonitorUpEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.latency, details.previousDownTime)
    }

    private fun TcpMonitorDownEvent.toParts() = toStructuredMessage().let { details ->
        listOfNotNull(getEmoji() + " " + details.summary, details.error, details.previousUpTime)
    }

    override fun toFormattedMessage(event: SSLMonitorEvent): String {
        val messageParts: List<String> = when (event) {
            is SSLValidEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + details.summary,
                    details.previousInvalidEvent
                )
            }
            is SSLWillExpireEvent -> event.toStructuredMessage().let { details ->
                listOf(
                    event.getEmoji() + " " + details.summary,
                    details.validUntil
                )
            }
            is SSLInvalidEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + details.summary,
                    details.error,
                    details.previousValidEvent
                )
            }
        }

        return messageParts.assemble()
    }

    fun toFormattedMessage(event: HttpRedirectEvent) = "${event.getEmoji()} ${event.toStructuredMessage().summary}"

    override fun toFormattedMessage(event: MaintenanceWindowEvent): String {
        val window = event.window
        return when (event) {
            is MaintenanceWindowStartEvent -> listOfNotNull(
                event.getEmoji() + " " + Messages.maintenanceWindowStarted(window.name),
                window.description?.takeIf { it.isNotBlank() },
            )

            is MaintenanceWindowEndEvent -> listOf(
                event.getEmoji() + " " + Messages.maintenanceWindowEnded(window.name),
            )
        }.assemble()
    }

    private fun List<String>.assemble(): String = joinToString(". ")
}
