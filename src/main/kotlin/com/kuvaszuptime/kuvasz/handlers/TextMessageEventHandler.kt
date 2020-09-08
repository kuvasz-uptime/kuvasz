package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.TextMessageService
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import org.slf4j.Logger

abstract class TextMessageEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val messageService: TextMessageService
) {

    internal abstract val logger: Logger

    internal abstract fun String.bold(): String

    internal abstract fun String.italic(): String

    init {
        subscribeToEvents()
    }

    @ExecuteOn(TaskExecutors.IO)
    internal fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { messageService.sendMessage(it.toFormattedMessage()).handleResponse() }
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { messageService.sendMessage(it.toFormattedMessage()).handleResponse() }
        }
        eventDispatcher.subscribeToSSLValidEvents { event ->
            logger.debug("An SSLValidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { messageService.sendMessage(it.toFormattedMessage()).handleResponse() }
        }
        eventDispatcher.subscribeToSSLInvalidEvents { event ->
            logger.debug("An SSLInvalidEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { messageService.sendMessage(it.toFormattedMessage()).handleResponse() }
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            logger.debug("An SSLWillExpireEvent has been received for monitor with ID: ${event.monitor.id}")
            event.runWhenStateChanges { messageService.sendMessage(it.toFormattedMessage()).handleResponse() }
        }
    }

    private fun Flowable<HttpResponse<String>>.handleResponse(): Disposable =
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

    private fun UptimeMonitorEvent.toFormattedMessage(): String {
        val messageParts: List<String> = when (this) {
            is MonitorUpEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    getEmoji() + " " + details.summary.bold(),
                    details.latency.italic(),
                    details.previousDownTime.orNull()
                )
            }
            is MonitorDownEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    getEmoji() + " " + details.summary.bold(),
                    details.previousUpTime.orNull()
                )
            }
        }

        return messageParts.assemble()
    }

    private fun SSLMonitorEvent.toFormattedMessage(): String {
        val messageParts: List<String> = when (this) {
            is SSLValidEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    getEmoji() + " " + details.summary.bold(),
                    details.previousInvalidEvent.orNull()
                )
            }
            is SSLWillExpireEvent -> toStructuredMessage().let { details ->
                listOf(
                    getEmoji() + " " + details.summary.bold(),
                    details.validUntil.italic()
                )
            }
            is SSLInvalidEvent -> toStructuredMessage().let { details ->
                listOfNotNull(
                    getEmoji() + " " + details.summary.bold(),
                    details.error.italic(),
                    details.previousValidEvent.orNull()
                )
            }
        }

        return messageParts.assemble()
    }

    private fun List<String>.assemble(): String = joinToString("\n")
}
