package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.*

object LogMessageFormatter : TextMessageFormatter {

    override fun toFormattedMessage(event: UptimeMonitorEvent): String {
        val messageParts: List<String> = when (event) {
            is MonitorUpEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + details.summary,
                    details.latency,
                    details.previousDownTime
                )
            }
            is MonitorDownEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + details.summary,
                    details.error,
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

    fun toFormattedMessage(event: RedirectEvent) = "${event.getEmoji()} ${event.toStructuredMessage().summary}"

    private fun List<String>.assemble(): String = joinToString(". ")
}
