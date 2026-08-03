package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.integrations.SMTPMailer
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(beans = [SMTPMailerConfig::class, EmailNotificationConfig::class])
class SMTPEventHandler(
    private val smtpMailer: SMTPMailer,
    eventDispatcher: EventDispatcher,
    integrationRepository: IntegrationRepository,
) : NotificationEventHandler(eventDispatcher, integrationRepository) {

    override val logger = loggerFor<SMTPEventHandler>()

    override val integrationType = IntegrationType.EMAIL

    init {
        logger.info("SMTPEventHandler has been successfully initialized")
    }

    override fun handleMaintenanceEvent(event: MaintenanceWindowEvent) {
        filterMaintenanceTargets(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            smtpMailer.sendAsync(emailFactory.fromMaintenanceEvent(event))
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            smtpMailer.sendAsync(emailFactory.fromUptimeEvent(event))
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            smtpMailer.sendAsync(emailFactory.fromSSLEvent(event))
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        filterTargetConfigs(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            smtpMailer.sendAsync(emailFactory.fromDnsRecordsChangedEvent(event))
        }
    }
}
