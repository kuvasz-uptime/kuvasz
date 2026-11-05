package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.DockerContainerRestartedEvent
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
            emailFactory.fromMaintenanceEvent(event).forEach(smtpMailer::sendAsync)
        }
    }

    override fun handleUptimeEvent(event: UptimeMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            emailFactory.fromUptimeEvent(event).forEach(smtpMailer::sendAsync)
        }
    }

    override fun handleSSLEvent(event: SSLMonitorEvent) {
        filterTargetConfigs(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            emailFactory.fromSSLEvent(event).forEach(smtpMailer::sendAsync)
        }
    }

    override fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent) {
        filterTargetConfigs(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            emailFactory.fromDnsRecordsChangedEvent(event).forEach(smtpMailer::sendAsync)
        }
    }

    override fun handleDockerContainerRestartedEvent(event: DockerContainerRestartedEvent) {
        filterTargetConfigs(event).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            emailFactory.fromDockerContainerRestartedEvent(event).forEach(smtpMailer::sendAsync)
        }
    }
}
