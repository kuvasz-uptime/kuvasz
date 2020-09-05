package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.config.handlers.TelegramEventHandlerConfig
import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.TelegramAPIMessage
import com.kuvaszuptime.kuvasz.models.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.TelegramAPIService
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
    private val telegramAPIService: TelegramAPIService,
    private val telegramEventHandlerConfig: TelegramEventHandlerConfig,
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
            event.runWhenStateChanges { telegramAPIService.sendMessage(it.toTelegramMessage()).handleResponse() }
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { telegramAPIService.sendMessage(it.toTelegramMessage()).handleResponse() }
        }
    }

    private fun UptimeMonitorEvent.toTelegramMessage(): TelegramAPIMessage =
        TelegramAPIMessage(
            text = toHTMLMessage(),
            chat_id = telegramEventHandlerConfig.chatId
        )

    private fun Flowable<HttpResponse<String>>.handleResponse() =
        subscribe(
            {
                logger.debug("A Telegram message to your configured webhook has been successfully sent")
            },
            { ex ->
                if (ex is HttpClientResponseException) {
                    val responseBody = ex.response.getBody(String::class.java)
                    logger.error("Telegram message cannot be delivered due to an error: $responseBody")
                }
            }
        )

    private fun UptimeMonitorEvent.toHTMLMessage() =
        when (this) {
            is MonitorUpEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    "${getEmoji()} <b>${details.summary}</b>",
                    "<i>${details.latency}</i>",
                    details.previousDownTime.orNull()
                )
            }
            is MonitorDownEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    "${getEmoji()} <b>${details.summary}</b>",
                    details.previousUpTime.orNull()
                )
            }
        }.joinToString("\n")
}
