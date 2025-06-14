package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.SMTPMailer
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory

@Context
@Requires(beans = [SMTPMailerConfig::class, EmailNotificationConfig::class])
class SMTPEventHandler(
    private val smtpMailer: SMTPMailer,
    private val eventDispatcher: EventDispatcher,
    integrationRepository: IntegrationRepository,
) : AbstractIntegrationProvider(integrationRepository) {
    companion object {
        private val logger = LoggerFactory.getLogger(SMTPEventHandler::class.java)
    }

    override val integrationType = IntegrationType.EMAIL

    init {
        subscribeToEvents()
        logger.info("SMTPEventHandler has been successfully initialized")
    }

    private fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLValidEvents { event ->
            logger.debug("An SSLValidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLInvalidEvents { event ->
            logger.debug("An SSLInvalidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            logger.debug("An SSLWillExpireEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
    }

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges { event ->
            if (this is MonitorUpEvent && previousEvent == null) {
                return@runWhenStateChanges
            }
            filterTargetConfigs(event.monitor.integrations).forEach { target ->
                val emailFactory = EmailFactory(target as EmailNotificationConfig)
                smtpMailer.sendAsync(emailFactory.fromMonitorEvent(event))
            }
        }
    }

    private fun SSLMonitorEvent.handle() {
        runWhenStateChanges { event ->
            if (this is SSLValidEvent && previousEvent == null) {
                return@runWhenStateChanges
            }
            filterTargetConfigs(event.monitor.integrations).forEach { target ->
                val emailFactory = EmailFactory(target as EmailNotificationConfig)
                smtpMailer.sendAsync(emailFactory.fromMonitorEvent(event))
            }
        }
    }
}
