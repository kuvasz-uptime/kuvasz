package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micronaut.context.annotation.Context
import org.jooq.Configuration
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

@Context
class DatabaseEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val sslEventRepository: SSLEventRepository,
    private val jooqConfig: Configuration
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseEventHandler::class.java)
    }

    init {
        subscribeToEvents()
    }

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
        currentEvent.previousEvent?.let { previousEvent ->
            if (currentEvent.statusNotEquals(previousEvent)) {
                DSL.using(jooqConfig).transaction { config ->
                    uptimeEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt, config)
                    uptimeEventRepository.insertFromMonitorEvent(currentEvent, config)
                }
            } else {
                uptimeEventRepository.updateEventUpdatedAt(previousEvent.id, currentEvent.dispatchedAt)
            }
        } ?: uptimeEventRepository.insertFromMonitorEvent(currentEvent)
    }

    private fun handleSSLMonitorEvent(currentEvent: SSLMonitorEvent) {
        currentEvent.previousEvent?.let { previousEvent ->
            if (currentEvent.statusNotEquals(previousEvent)) {
                DSL.using(jooqConfig).transaction { config ->
                    sslEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt, config)
                    sslEventRepository.insertFromMonitorEvent(currentEvent, config)
                }
            } else {
                sslEventRepository.updateEventUpdatedAt(previousEvent.id, currentEvent.dispatchedAt)
            }
        } ?: sslEventRepository.insertFromMonitorEvent(currentEvent)
    }
}
