package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.RedirectEvent
import com.kuvaszuptime.kuvasz.models.toMessage
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

    init {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.info(event.toMessage())
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.error(event.toMessage())
        }
        eventDispatcher.subscribeToRedirectEvents { event ->
            logger.warn(event.toLogMessage())
        }
    }

    private fun RedirectEvent.toLogMessage() =
        "Request to \"${monitor.name}\" (${monitor.url}) has been redirected"
}
