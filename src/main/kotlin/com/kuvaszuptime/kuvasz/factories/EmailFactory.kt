package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.config.handlers.EmailEventHandlerConfig
import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.util.toEmoji
import com.kuvaszuptime.kuvasz.util.toStructuredMessage
import com.kuvaszuptime.kuvasz.util.toUptimeStatus
import org.simplejavamail.api.email.Email
import org.simplejavamail.email.EmailBuilder

class EmailFactory(private val config: EmailEventHandlerConfig) {

    fun fromUptimeMonitorEvent(event: UptimeMonitorEvent): Email =
        createEmailBase()
            .withSubject(event.getSubject())
            .withPlainText(event.toMessage())
            .buildEmail()

    private fun UptimeMonitorEvent.getSubject(): String =
        "[kuvasz-uptime] - ${toEmoji()} [${monitor.name}] ${monitor.url} is ${toUptimeStatus()}"

    private fun createEmailBase() =
        EmailBuilder
            .startingBlank()
            .to(config.to, config.to)
            .from(config.from, config.from)

    private fun UptimeMonitorEvent.toMessage() =
        when (this) {
            is MonitorUpEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.latency,
                    details.previousDownTime.orNull()
                )
            }
            is MonitorDownEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    details.summary,
                    details.error,
                    details.previousUpTime.orNull()
                )
            }
        }.joinToString("\n")
}
