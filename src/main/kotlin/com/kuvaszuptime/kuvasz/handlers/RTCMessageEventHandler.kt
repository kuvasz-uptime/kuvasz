package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.formatters.RichTextMessageFormatter
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.TextMessageService
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import org.slf4j.Logger

abstract class RTCMessageEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val messageService: TextMessageService
) {

    internal abstract val logger: Logger

    internal abstract val formatter: RichTextMessageFormatter

    init {
        subscribeToEvents()
    }

    @ExecuteOn(TaskExecutors.IO)
    internal fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLValidEvents { event ->
            logger.debug("An SSLValidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLInvalidEvents { event ->
            logger.debug("An SSLInvalidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            logger.debug("An SSLWillExpireEvent has been received for monitor with ID: ${event.monitor.id}")
            event.handle()
        }
    }

    private fun UptimeMonitorEvent.handle() =
        this.runWhenStateChanges { event ->
            val message = formatter.toFormattedMessage(event)
            messageService.sendMessage(message).handleResponse()
        }

    private fun SSLMonitorEvent.handle() =
        this.runWhenStateChanges { event ->
            val message = formatter.toFormattedMessage(event)
            messageService.sendMessage(message).handleResponse()
        }

    private fun Single<String>.handleResponse(): Disposable =
        subscribe(
            {
                logger.debug("The message to your configured webhook has been successfully sent")
            },
            { ex ->
                if (ex is HttpClientResponseException) {
                    val responseBody = ex.response.getBody(String::class.java)
                    logger.error("The message cannot be sent to your configured webhook: $responseBody")
                }
            }
        )
}
