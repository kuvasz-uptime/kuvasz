package com.kuvaszuptime.kuvasz.formatters

import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.UptimeMonitorEvent

abstract class TextMessageFormatter {
    abstract fun bold(input: String): String

    abstract fun italic(input: String): String

    fun toFormattedMessage(event: UptimeMonitorEvent): String {
        val messageParts: List<String> = when (event) {
            is MonitorUpEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    italic(details.latency),
                    details.previousDownTime.orNull()
                )
            }
            is MonitorDownEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    details.previousUpTime.orNull()
                )
            }
        }

        return messageParts.assemble()
    }

    fun toFormattedMessage(event: SSLMonitorEvent): String {
        val messageParts: List<String> = when (event) {
            is SSLValidEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    event.getEmoji() + " " + bold(details.summary),
                    details.previousInvalidEvent.orNull()
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
                    details.previousValidEvent.orNull()
                )
            }
        }

        return messageParts.assemble()
    }

    private fun List<String>.assemble(): String = joinToString("\n")
}
