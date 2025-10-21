package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent

object PlainTextMessageFormatter : TextMessageFormatter {

    override fun toFormattedMessage(event: UptimeMonitorEvent): String {
        val messageParts: List<String> = when (event) {
            is HttpMonitorUpEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.latency,
                    details.previousDownTime
                )
            }

            is HttpMonitorDownEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.error,
                    details.previousUpTime
                )
            }

            is PushMonitorDownEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.error,
                    details.previousUpTime
                )
            }

            is PushMonitorUpEvent -> event.toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.previousDownTime
                )
            }
        }

        return messageParts.assemble()
    }

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

    private fun List<String>.assemble(): String = joinToString("\n")
}
