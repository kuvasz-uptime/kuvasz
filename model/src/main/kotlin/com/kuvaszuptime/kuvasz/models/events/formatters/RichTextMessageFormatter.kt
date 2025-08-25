package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent

abstract class RichTextMessageFormatter : TextMessageFormatter {
    abstract fun bold(input: String): String

    abstract fun italic(input: String): String

    override fun toFormattedMessage(event: HttpUptimeMonitorEvent): String {
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

    private fun List<String>.assemble(): String = joinToString("\n")
}
