package com.akobor.kuvasz.handlers

import com.akobor.kuvasz.events.UptimeMonitorEvent
import com.akobor.kuvasz.events.toUptimeStatus
import com.akobor.kuvasz.repositories.LatencyLogRepository
import com.akobor.kuvasz.repositories.UptimeEventRepository
import com.akobor.kuvasz.services.EventDispatcher
import com.akobor.kuvasz.util.transaction
import io.micronaut.context.annotation.Context
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
        // TODO debug logging
    }

    init {
        subscribeToMonitorUpEvents()
        subscribeToMonitorDownEvents()
    }

    private fun subscribeToMonitorUpEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { monitorUpEvent ->
            latencyLogRepository.insertLatencyForMonitor(monitorUpEvent.monitor.id, monitorUpEvent.latency)
            handleUptimeMonitorEvent(monitorUpEvent)
        }
    }

    private fun subscribeToMonitorDownEvents() {
        eventDispatcher.subscribeToMonitorDownEvents { handleUptimeMonitorEvent(it) }
    }

    private fun handleUptimeMonitorEvent(currentEvent: UptimeMonitorEvent) {
        currentEvent.previousEvent.fold(
            { uptimeEventRepository.insertFromMonitorEvent(currentEvent) },
            { previousEvent ->
                if (previousEvent.status != currentEvent.toUptimeStatus()) {
                    uptimeEventRepository.transaction {
                        uptimeEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt)
                        uptimeEventRepository.insertFromMonitorEvent(currentEvent)
                    }
                }
            }
        )
    }
}
