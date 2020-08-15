package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.config.handlers.EmailEventHandlerConfig
import com.kuvaszuptime.kuvasz.models.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.toMessage
import com.kuvaszuptime.kuvasz.models.toUptimeStatus
import org.simplejavamail.api.email.Email
import org.simplejavamail.email.EmailBuilder

class EmailFactory(private val config: EmailEventHandlerConfig) {

    fun fromUptimeMonitorEvent(event: UptimeMonitorEvent): Email =
        createEmailBase()
            .withSubject(event.getSubject())
            .withPlainText(event.toMessage())
            .buildEmail()

    private fun UptimeMonitorEvent.getSubject(): String =
        "[kuvasz-uptime] - [${monitor.name}] ${monitor.url} is ${toUptimeStatus()}"

    private fun createEmailBase() =
        EmailBuilder
            .startingBlank()
            .to(config.to, config.to)
            .from(config.from, config.from)
}
