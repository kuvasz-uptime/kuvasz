package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.DnsRecordsChangedEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.slf4j.Logger

abstract class NotificationEventHandler(
    eventDispatcher: EventDispatcher,
    integrationRepository: IntegrationRepository,
) : AbstractIntegrationProvider(integrationRepository) {

    internal abstract val logger: Logger

    init {
        eventDispatcher.subscribeToUptimeMonitorEvents { event ->
            event.logReceipt()
            event.runWhenStateChanges { stateChange ->
                if (stateChange.isUp() && stateChange.previousEvent == null) {
                    return@runWhenStateChanges
                }
                handleUptimeEvent(stateChange)
            }
        }
        eventDispatcher.subscribeToSSLMonitorEvents { event ->
            event.logReceipt()
            event.runWhenStateChanges { stateChange ->
                if (stateChange is SSLValidEvent && stateChange.previousEvent == null) {
                    return@runWhenStateChanges
                }
                handleSSLEvent(stateChange)
            }
        }
        eventDispatcher.subscribeToMaintenanceWindowEvents { event ->
            logger.debug(
                "A ${event::class.simpleName} has been received for window with ID: ${event.window.id}"
            )
            handleMaintenanceEvent(event)
        }
        eventDispatcher.subscribeToDnsRecordsChangedEvents { event ->
            logger.debug(
                "A ${event::class.simpleName} has been received for monitor with ID: ${event.monitor.id}"
            )
            handleDnsRecordsChangedEvent(event)
        }
    }

    protected abstract fun handleUptimeEvent(event: UptimeMonitorEvent)

    protected abstract fun handleSSLEvent(event: SSLMonitorEvent)

    protected abstract fun handleMaintenanceEvent(event: MaintenanceWindowEvent)

    protected abstract fun handleDnsRecordsChangedEvent(event: DnsRecordsChangedEvent)

    private fun UptimeMonitorEvent.logReceipt() =
        logger.debug("A ${this::class.simpleName} has been received for monitor with ID: ${monitor.id}")

    private fun SSLMonitorEvent.logReceipt() =
        logger.debug("A ${this::class.simpleName} has been received for monitor with ID: ${monitor.id}")

    protected fun Single<*>.handleResponse(): Disposable =
        subscribeOn(Schedulers.io()).subscribe(
            {
                logger.debug("The event has been successfully sent to $integrationType")
            },
            { ex ->
                val message = if (ex is HttpClientResponseException) {
                    ex.response.getBodyAs<String>() ?: "Empty response"
                } else {
                    ex.message
                }
                logger.error("The event cannot be sent to $integrationType: $message")
            }
        )
}
