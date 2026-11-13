package com.kuvaszuptime.kuvasz.factories

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.PlainTextMessageFormatter
import com.kuvaszuptime.kuvasz.models.events.formatters.getEmoji
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.recipientAddresses
import com.kuvaszuptime.kuvasz.models.handlers.senderName
import jakarta.mail.Message
import org.simplejavamail.api.email.Email
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.recipient.RecipientBuilder

class EmailFactory(private val config: EmailNotificationConfig) {

    private val formatter = PlainTextMessageFormatter

    fun fromUptimeEvent(event: UptimeMonitorEvent): List<Email> =
        buildEmails(event.getSubject(), formatter.toFormattedMessage(event))

    fun fromSSLEvent(event: SSLMonitorEvent): List<Email> =
        buildEmails(event.getSubject(), formatter.toFormattedMessage(event))

    fun fromMaintenanceEvent(event: MaintenanceWindowEvent): List<Email> =
        buildEmails(event.getSubject(), formatter.toFormattedMessage(event))

    fun fromDnsRecordsChangedEvent(event: DnsRecordsChangedEvent): List<Email> =
        buildEmails(event.getSubject(), formatter.toFormattedMessage(event))

    fun fromDockerContainerRestartedEvent(event: DockerContainerRestartedEvent): List<Email> =
        buildEmails(event.getSubject(), formatter.toFormattedMessage(event))

    fun testEmails(): List<Email> =
        buildEmails(Messages.integrationTestMessage(), Messages.integrationTestMessage())

    private fun UptimeMonitorEvent.getSubject(): String =
        "[kuvasz-uptime] - ${getEmoji()} [${monitor.name}] is $uptimeStatus"

    private fun SSLMonitorEvent.getSubject(): String {
        val statusString = when (sslStatus) {
            SslStatus.VALID -> Messages.hasAValidCertificate()
            SslStatus.INVALID -> Messages.hasAnInvalidCertificate()
            SslStatus.WILL_EXPIRE -> Messages.hasAnExpiringCertificate()
        }

        return "[kuvasz-uptime] - ${getEmoji()} [${monitor.name}] $statusString"
    }

    private fun DnsRecordsChangedEvent.getSubject(): String =
        "[kuvasz-uptime] - ${getEmoji()} [${monitor.name}] ${Messages.dnsRecordsHaveChanged()}"

    private fun DockerContainerRestartedEvent.getSubject(): String =
        "[kuvasz-uptime] - ${getEmoji()} [${monitor.name}] ${Messages.dockerContainerHasRestarted()}"

    private fun MaintenanceWindowEvent.getSubject(): String {
        val statusString = when (this) {
            is MaintenanceWindowStartEvent -> Messages.maintenanceWindowStarted(window.name)
            is MaintenanceWindowEndEvent -> Messages.maintenanceWindowEnded(window.name)
        }

        return "[kuvasz-uptime] - ${getEmoji()} $statusString"
    }

    /**
     * Builds one email with every recipient in To, or one email per recipient if
     * [EmailNotificationConfig.separateEmails] is enabled.
     */
    private fun buildEmails(subject: String, plainText: String): List<Email> {
        val recipientGroups = config.recipientAddresses().let { addresses ->
            if (config.separateEmails) addresses.map(::listOf) else listOf(addresses)
        }
        val senderName = config.senderName() ?: config.fromAddress

        return recipientGroups.map { addresses ->
            EmailBuilder
                .startingBlank()
                .withRecipients(addresses.map { address -> address.toRecipient() })
                .from(senderName, config.fromAddress)
                .withSubject(subject)
                .withPlainText(plainText)
                .buildEmail()
        }
    }

    private fun String.toRecipient() = RecipientBuilder()
        .withAddress(this)
        .withType(Message.RecipientType.TO)
        .build()
}
