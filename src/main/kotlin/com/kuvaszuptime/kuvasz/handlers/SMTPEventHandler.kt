package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.config.handlers.SMTPEventHandlerConfig
import com.kuvaszuptime.kuvasz.factories.EmailFactory
import com.kuvaszuptime.kuvasz.models.runWhenStateChanges
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.SMTPMailer
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory
import javax.inject.Inject

@Context
@Requires(property = "handler-config.smtp-event-handler.enabled", value = "true")
class SMTPEventHandler @Inject constructor(
    smtpEventHandlerConfig: SMTPEventHandlerConfig,
    private val smtpMailer: SMTPMailer,
    private val eventDispatcher: EventDispatcher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SMTPEventHandler::class.java)
    }

    private val emailFactory = EmailFactory(smtpEventHandlerConfig)

    init {
        subscribeToEvents()
    }

    private fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { smtpMailer.sendAsync(emailFactory.fromUptimeMonitorEvent(it)) }
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { smtpMailer.sendAsync(emailFactory.fromUptimeMonitorEvent(it)) }
        }
    }
}
