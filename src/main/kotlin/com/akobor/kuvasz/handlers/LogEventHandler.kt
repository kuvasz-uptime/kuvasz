package com.akobor.kuvasz.handlers

import com.akobor.kuvasz.events.MonitorDownEvent
import com.akobor.kuvasz.events.MonitorUpEvent
import com.akobor.kuvasz.events.RedirectEvent
import com.akobor.kuvasz.events.getEndedEventDuration
import com.akobor.kuvasz.services.EventDispatcher
import io.micronaut.context.annotation.Context
import org.slf4j.LoggerFactory
import javax.inject.Inject
import kotlin.time.ExperimentalTime

@ExperimentalTime
@Context
class LogEventHandler @Inject constructor(private val eventDispatcher: EventDispatcher) : EventHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(LogEventHandler::class.java)
    }

    init {
        subscribeToMonitorUpEvents()
        subscribeToMonitorDownEvents()
        subscribeToRedirectEvents()
    }

    private fun subscribeToMonitorUpEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.info(event.toLogMessage())
        }
    }

    private fun subscribeToMonitorDownEvents() {
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.error(event.toLogMessage())
        }
    }

    private fun subscribeToRedirectEvents() {
        eventDispatcher.subscribeToRedirectEvents { event ->
            logger.warn(event.toLogMessage())
        }
    }

    private fun MonitorUpEvent.toLogMessage(): String {
        val message = "\"${monitor.name}\" (${monitor.url}) is UP (${status.code}). Latency was: ${latency}ms."
        // TODO refactor this
        return getEndedEventDuration().fold(
            { message },
            { duration ->
                duration.toComponents { _, hours, minutes, seconds, _ ->
                    "$message Was down for $hours hour(s), $minutes minute(s), $seconds second(s)."
                }
            }
        )
    }

    private fun MonitorDownEvent.toLogMessage(): String {
        val message = "\"${monitor.name}\" (${monitor.url}) is DOWN. Reason: ${error.message}"
        // TODO refactor this
        return getEndedEventDuration().fold(
            { message },
            { duration ->
                duration.toComponents { _, hours, minutes, seconds, _ ->
                    "$message Was up for $hours hour(s), $minutes minute(s), $seconds second(s)."
                }
            }
        )
    }

    private fun RedirectEvent.toLogMessage() =
        "Request to \"${monitor.name}\" (${monitor.url}) has been redirected"
}
