package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.HttpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.LogMessageFormatter
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory

@Context
@Requires(property = "app-config.log-event-handler", value = "true")
class LogEventHandler(eventDispatcher: EventDispatcher) {
    companion object {
        private val logger = LoggerFactory.getLogger(LogEventHandler::class.java)
    }

    private val formatter = LogMessageFormatter

    init {
        eventDispatcher.subscribeToHttpMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToHttpMonitorDownEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToHttpRedirectEvents { event ->
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

    private fun HttpUptimeMonitorEvent.handle() {
        val message = formatter.toFormattedMessage(this)
        logger.info(message)
    }

    private fun SSLMonitorEvent.handle() {
        val message = formatter.toFormattedMessage(this)
        logger.info(message)
    }

    private fun HttpRedirectEvent.handle() {
        val message = formatter.toFormattedMessage(this)
        logger.info(message)
    }
}
