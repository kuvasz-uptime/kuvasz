package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micronaut.context.annotation.Context
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

@Context
class DatabaseEventHandler(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository,
    private val latencyLogRepository: LatencyLogRepository,
    private val sslEventRepository: SSLEventRepository,
    private val dslContext: DSLContext,
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
            if (event.monitor.latencyHistoryEnabled) {
                latencyLogRepository.insertLatencyForMonitor(event.monitor.id, event.latency)
            }
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
            logger.debug(
                "A previous event was found for [${currentEvent.monitor.name}] with ID: ${previousEvent.id} " +
                    "and status: ${previousEvent.status}. The current event's status is ${currentEvent.uptimeStatus}."
            )
            if (currentEvent.statusNotEquals(previousEvent)) {
                logger.debug(
                    "[${currentEvent.monitor.name}] The status of the previous event is different from the " +
                        "current event. Ending the previous event and inserting a new one."
                )
                dslContext.transaction { config ->
                    uptimeEventRepository.endEventById(
                        eventId = previousEvent.id,
                        endedAt = currentEvent.dispatchedAt,
                        ctx = config.dsl(),
                    )
                    uptimeEventRepository.insertFromMonitorEvent(currentEvent, config.dsl())
                    logger.debug(
                        "[${currentEvent.monitor.name}] The previous event has been ended and a new one " +
                            "has been inserted."
                    )
                }
            } else {
                logger.debug(
                    "[${currentEvent.monitor.name}] The status of the previous event is the same as the current " +
                        "event. Updating the updatedAt timestamp of the previous event."
                )
                uptimeEventRepository.updateEventUpdatedAt(previousEvent.id, currentEvent.dispatchedAt)
            }
        } ?: run {
            logger.debug("A previous event was not found for [${currentEvent.monitor.name}], creating a new one")
            uptimeEventRepository.insertFromMonitorEvent(currentEvent)
        }
    }

    private fun handleSSLMonitorEvent(currentEvent: SSLMonitorEvent) {
        currentEvent.previousEvent?.let { previousEvent ->
            if (currentEvent.statusNotEquals(previousEvent)) {
                dslContext.transaction { config ->
                    sslEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt, config.dsl())
                    sslEventRepository.insertFromMonitorEvent(currentEvent, config.dsl())
                }
            } else {
                sslEventRepository.updateEventUpdatedAt(previousEvent.id, currentEvent.dispatchedAt)
            }
        } ?: sslEventRepository.insertFromMonitorEvent(currentEvent)
    }
}
