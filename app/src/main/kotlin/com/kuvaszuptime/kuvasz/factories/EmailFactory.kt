package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.config.handlers.EmailEventHandlerConfig
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.PlainTextMessageFormatter
import com.kuvaszuptime.kuvasz.models.events.formatters.getEmoji
import org.simplejavamail.api.email.Email
import org.simplejavamail.email.EmailBuilder

class EmailFactory(private val config: EmailEventHandlerConfig) {

    private val formatter = PlainTextMessageFormatter

    fun fromMonitorEvent(event: UptimeMonitorEvent): Email =
        createEmailBase()
            .withSubject(event.getSubject())
            .withPlainText(formatter.toFormattedMessage(event))
            .buildEmail()

    fun fromMonitorEvent(event: SSLMonitorEvent): Email =
        createEmailBase()
            .withSubject(event.getSubject())
            .withPlainText(formatter.toFormattedMessage(event))
            .buildEmail()

    private fun UptimeMonitorEvent.getSubject(): String =
        "[kuvasz-uptime] - ${getEmoji()} [${monitor.name}] ${monitor.url} is $uptimeStatus"

    private fun SSLMonitorEvent.getSubject(): String {
        val statusString = when (sslStatus) {
            SslStatus.VALID -> Messages.hasAValidCertificate()
            SslStatus.INVALID -> Messages.hasAnInvalidCertificate()
            SslStatus.WILL_EXPIRE -> Messages.hasAnExpiringCertificate()
        }

        return "[kuvasz-uptime] - ${getEmoji()} [${monitor.name}] ${monitor.url} $statusString"
    }

    private fun createEmailBase() =
        EmailBuilder
            .startingBlank()
            .to(config.to, config.to)
            .from(config.from, config.from)
}
