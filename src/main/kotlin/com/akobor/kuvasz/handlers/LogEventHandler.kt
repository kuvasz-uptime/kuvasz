package com.akobor.kuvasz.handlers

import com.akobor.kuvasz.events.MonitorDownEvent
import com.akobor.kuvasz.events.MonitorUpEvent
import com.akobor.kuvasz.events.RedirectEvent
import com.akobor.kuvasz.events.getEndedEventDuration
import com.akobor.kuvasz.services.EventDispatcher
import com.akobor.kuvasz.util.toDurationString
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import org.slf4j.LoggerFactory
import javax.inject.Inject

@Context
@Requires(property = "app-config.log-event-handler.enabled", value = "true")
class LogEventHandler @Inject constructor(eventDispatcher: EventDispatcher) : EventHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(LogEventHandler::class.java)
    }

    init {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.info(event.toLogMessage())
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.error(event.toLogMessage())
        }
        eventDispatcher.subscribeToRedirectEvents { event ->
            logger.warn(event.toLogMessage())
        }
    }

    private fun MonitorUpEvent.toLogMessage(): String {
        val message = "\"${monitor.name}\" (${monitor.url}) is UP (${status.code}). Latency was: ${latency}ms."
        return getEndedEventDuration().toDurationString().fold(
            { message },
            { "$message Was down for $it." }
        )
    }

    private fun MonitorDownEvent.toLogMessage(): String {
        val message = "\"${monitor.name}\" (${monitor.url}) is DOWN. Reason: ${error.message}."
        return getEndedEventDuration().toDurationString().fold(
            { message },
            { "$message Was up for $it." }
        )
    }

    private fun RedirectEvent.toLogMessage() =
        "Request to \"${monitor.name}\" (${monitor.url}) has been redirected"
}
