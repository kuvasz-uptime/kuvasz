package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.RedirectEvent
import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.SSLWillExpireEvent
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
            logger.info(event.toLogMessage())
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.error(event.toLogMessage())
        }
        eventDispatcher.subscribeToRedirectEvents { event ->
            logger.warn(event.toLogMessage())
        }
        eventDispatcher.subscribeToSSLValidEvents { event ->
            logger.info(event.toLogMessage())
        }
        eventDispatcher.subscribeToSSLInvalidEvents { event ->
            logger.info(event.toLogMessage())
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            logger.info(event.toLogMessage())
        }
    }

    private fun MonitorUpEvent.toLogMessage() =
        toStructuredMessage()
            .let { details ->
                listOfNotNull(
                    "${getEmoji()} ${details.summary}",
                    details.latency,
                    details.previousDownTime.orNull()
                ).joinToString(". ")
            }

    private fun MonitorDownEvent.toLogMessage(): String =
        toStructuredMessage()
            .let { details ->
                listOfNotNull(
                    "${getEmoji()} ${details.summary}",
                    details.error,
                    details.previousUpTime.orNull()
                ).joinToString(". ")
            }

    private fun RedirectEvent.toLogMessage() =
        "${getEmoji()} Request to \"${monitor.name}\" (${monitor.url}) has been redirected"

    private fun SSLValidEvent.toLogMessage(): String =
        toStructuredMessage()
            .let { details ->
                listOfNotNull(
                    "${getEmoji()} ${details.summary}",
                    details.previousInvalidEvent.orNull()
                ).joinToString(". ")
            }

    private fun SSLInvalidEvent.toLogMessage(): String =
        toStructuredMessage()
            .let { details ->
                listOfNotNull(
                    "${getEmoji()} ${details.summary}",
                    details.error,
                    details.previousValidEvent.orNull()
                ).joinToString(". ")
            }

    private fun SSLWillExpireEvent.toLogMessage(): String =
        toStructuredMessage().let { details ->
            "${getEmoji()} ${details.summary}. ${details.validUntil}"
        }
}
