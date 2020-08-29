package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.TelegramWebhookMessage
import com.kuvaszuptime.kuvasz.models.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.runWhenStateChanges
import com.kuvaszuptime.kuvasz.models.toEmoji
import com.kuvaszuptime.kuvasz.models.toStructuredMessage
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.TelegramWebhookService
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.reactivex.Flowable
import org.slf4j.LoggerFactory

@Context
@Requires(property = "handler-config.telegram-event-handler.enabled", value = "true")
class TelegramEventHandler(
    private val telegramEventHandler: TelegramWebhookService,
    private val eventDispatcher: EventDispatcher
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TelegramEventHandler::class.java)
    }

    init {
        subscribeToEvents()
    }

    @ExecuteOn(TaskExecutors.IO)
    private fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { telegramEventHandler.sendMessage(it.toTelegramMessage()).handleResponse() }
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { telegramEventHandler.sendMessage(it.toTelegramMessage()).handleResponse() }
        }
    }

    private fun UptimeMonitorEvent.toTelegramMessage() = TelegramWebhookMessage(text = "${toEmoji()} ${toMessage()}")

    private fun Flowable<HttpResponse<String>>.handleResponse() =
        subscribe(
            {
                logger.debug("A Telegram message to your configured webhook has been successfully sent")
            },
            { ex ->
                if (ex is HttpClientResponseException) {
                    val responseBody = ex.response.getBody(String::class.java)
                    logger.error("Telegram message cannot be sent to your configured webhook: $responseBody")
                }
            }
        )

    private fun UptimeMonitorEvent.toMessage() =
        when (this) {
            is MonitorUpEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    "*${details.summary}*",
                    "_${details.latency}_",
                    details.previousDownTime.orNull()
                )
            }
            is MonitorDownEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    "*${details.summary}*",
                    details.previousUpTime.orNull()
                )
            }
        }.joinToString("\n")
}
