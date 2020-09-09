package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.LogMessageFormatter
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory
import javax.inject.Inject

@Context
@Requires(property = "handler-config.log-event-handler.enabled", value = "true")
class LogEventHandler @Inject constructor(eventDispatcher: EventDispatcher) {
    companion object {
        private val logger = LoggerFactory.getLogger(LogEventHandler::class.java)
    }

    private val formatter = LogMessageFormatter

    init {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToRedirectEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToSSLValidEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToSSLInvalidEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            event.handle()
        }
    }

    private fun UptimeMonitorEvent.handle() {
        val message = formatter.toFormattedMessage(this)
        logger.info(message)
    }

    private fun SSLMonitorEvent.handle() {
        val message = formatter.toFormattedMessage(this)
        logger.info(message)
    }

    private fun RedirectEvent.handle() {
        val message = formatter.toFormattedMessage(this)
        logger.info(message)
    }
}
