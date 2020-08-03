package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.events.uptimeStatusNotEquals
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
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
    private val latencyLogRepository: LatencyLogRepository
) : EventHandler {
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
    }

    private fun handleUptimeMonitorEvent(currentEvent: UptimeMonitorEvent) {
        currentEvent.previousEvent.fold(
            { uptimeEventRepository.insertFromMonitorEvent(currentEvent) },
            { previousEvent ->
                if (currentEvent.uptimeStatusNotEquals(previousEvent)) {
                    uptimeEventRepository.transaction {
                        uptimeEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt)
                        uptimeEventRepository.insertFromMonitorEvent(currentEvent)
                    }
                }
            }
        )
    }
}
