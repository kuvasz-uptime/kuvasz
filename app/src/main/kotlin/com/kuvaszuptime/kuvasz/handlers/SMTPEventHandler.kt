package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
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
    private val eventDispatcher: EventDispatcher,
    integrationRepository: IntegrationRepository,
) : AbstractIntegrationProvider(integrationRepository) {
    companion object {
        private val logger = loggerFor<SMTPEventHandler>()
    }

    override val integrationType = IntegrationType.EMAIL

    init {
        subscribeToEvents()
        logger.info("SMTPEventHandler has been successfully initialized")
    }

    private fun subscribeToEvents() {
        eventDispatcher.subscribeToHttpMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToHttpMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToPushMonitorEvents { event ->
            logger.debug(
                "A PushMonitorEvent (${event.toIntegrationEventType()}) has been received for " +
                    "monitor with ID: ${event.monitor.id}"
            )
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
        eventDispatcher.subscribeToIcmpMonitorUpEvents { event ->
            logger.debug("An IcmpMonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToIcmpMonitorDownEvents { event ->
            logger.debug("An IcmpMonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToMaintenanceStartEvents { event ->
            logger.debug("A MaintenanceWindowStartEvent has been received for window with ID: ${event.window.id}")
            event.handle()
        }
        eventDispatcher.subscribeToMaintenanceEndEvents { event ->
            logger.debug("A MaintenanceWindowEndEvent has been received for window with ID: ${event.window.id}")
            event.handle()
        }
    }

    private fun MaintenanceWindowEvent.handle() {
        filterMaintenanceTargets(this).forEach { target ->
            val emailFactory = EmailFactory(target as EmailNotificationConfig)
            smtpMailer.sendAsync(emailFactory.fromMaintenanceEvent(this))
        }
    }

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges { event ->
            if (this.isUp() && previousEvent == null) {
                return@runWhenStateChanges
            }
            filterTargetConfigs(event).forEach { target ->
                val emailFactory = EmailFactory(target as EmailNotificationConfig)
                smtpMailer.sendAsync(emailFactory.fromUptimeEvent(event))
            }
        }
    }

    private fun SSLMonitorEvent.handle() {
        runWhenStateChanges { event ->
            if (this is SSLValidEvent && previousEvent == null) {
                return@runWhenStateChanges
            }
            filterTargetConfigs(event).forEach { target ->
                val emailFactory = EmailFactory(target as EmailNotificationConfig)
                smtpMailer.sendAsync(emailFactory.fromSSLEvent(event))
            }
        }
    }
}
