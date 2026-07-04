package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.LogMessageFormatter
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires

@Context
@Requires(property = "app-config.log-event-handler", value = "true")
class LogEventHandler(eventDispatcher: EventDispatcher) {
    companion object {
        private val logger = loggerFor<LogEventHandler>()
    }

    private val formatter = LogMessageFormatter

    init {
        eventDispatcher.subscribeToHttpMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToHttpMonitorDownEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToPushMonitorEvents { event ->
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
        eventDispatcher.subscribeToIcmpMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToIcmpMonitorDownEvents { event ->
            event.handle()
        }
    }

    private fun UptimeMonitorEvent.handle() {
        this.runWhenStateChanges { event ->
            val message = formatter.toFormattedMessage(event)
            logger.info(message)
        }
    }

    private fun SSLMonitorEvent.handle() {
        this.runWhenStateChanges { event ->
            val message = formatter.toFormattedMessage(event)
            logger.info(message)
        }
    }

    private fun HttpRedirectEvent.handle() {
        val message = formatter.toFormattedMessage(this)
        logger.info(message)
    }
}
