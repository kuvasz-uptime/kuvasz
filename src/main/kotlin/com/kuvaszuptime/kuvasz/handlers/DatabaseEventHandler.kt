package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.transaction
import io.micronaut.context.annotation.Context
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import org.slf4j.LoggerFactory
import javax.inject.Inject

@Context
class DatabaseEventHandler @Inject constructor(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val sslEventRepository: SSLEventRepository
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseEventHandler::class.java)
    }

    init {
        subscribeToEvents()
    }

    @ExecuteOn(TaskExecutors.IO)
    private fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            logger.debug("A MonitorUpEvent has been received for monitor with ID: ${event.monitor.id}")
            latencyLogRepository.insertLatencyForMonitor(event.monitor.id, event.latency)
            handleUptimeMonitorEvent(event)
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            logger.debug("A MonitorDownEvent has been received for monitor with ID: ${event.monitor.id}")
            handleUptimeMonitorEvent(event)
        }
        eventDispatcher.subscribeToSSLValidEvents { event ->
            logger.debug("An SSLValidEvent has been received for monitor with ID: ${event.monitor.id}")
            handleSSLMonitorEvent(event)
        }
        eventDispatcher.subscribeToSSLInvalidEvents { event ->
            logger.debug("An SSLInvalidEvent has been received for monitor with ID: ${event.monitor.id}")
            handleSSLMonitorEvent(event)
        }
        eventDispatcher.subscribeToSSLWillExpireEvents { event ->
            logger.debug("An SSLWillExpireEvent has been received for monitor with ID: ${event.monitor.id}")
            handleSSLMonitorEvent(event)
        }
    }

    private fun handleUptimeMonitorEvent(currentEvent: UptimeMonitorEvent) {
        currentEvent.previousEvent.fold(
            { uptimeEventRepository.insertFromMonitorEvent(currentEvent) },
            { previousEvent ->
                if (currentEvent.statusNotEquals(previousEvent)) {
                    uptimeEventRepository.transaction {
                        uptimeEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt)
                        uptimeEventRepository.insertFromMonitorEvent(currentEvent)
                    }
                } else {
                    uptimeEventRepository.updateEventUpdatedAt(previousEvent.id, currentEvent.dispatchedAt)
                }
            }
        )
    }

    private fun handleSSLMonitorEvent(currentEvent: SSLMonitorEvent) {
        currentEvent.previousEvent.fold(
            { sslEventRepository.insertFromMonitorEvent(currentEvent) },
            { previousEvent ->
                if (currentEvent.statusNotEquals(previousEvent)) {
                    sslEventRepository.transaction {
                        sslEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt)
                        sslEventRepository.insertFromMonitorEvent(currentEvent)
                    }
                } else {
                    sslEventRepository.updateEventUpdatedAt(previousEvent.id, currentEvent.dispatchedAt)
                }
            }
        )
    }
}
