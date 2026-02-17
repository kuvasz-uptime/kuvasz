package com.kuvaszuptime.kuvasz.handlers

import com.kuvaszuptime.kuvasz.jooq.UptimeEventRecord
import com.kuvaszuptime.kuvasz.models.events.HttpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SSLEventRepository
import jakarta.inject.Singleton
import org.jooq.DSLContext
import org.slf4j.LoggerFactory

@Singleton
class DatabaseEventHandler(
    private val httpUptimeEventRepository: HttpUptimeEventRepository,
    private val pushUptimeEventRepository: PushUptimeEventRepository,
    private val sslEventRepository: SSLEventRepository,
    private val dslContext: DSLContext,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(DatabaseEventHandler::class.java)
    }

    fun handleUptimeMonitorEvent(currentEvent: UptimeMonitorEvent) {
        val monitor = currentEvent.monitor

        currentEvent.previousEvent?.let { previousEvent ->
            logger.debug(
                "A previous event was found for [${monitor.name}] with ID: ${previousEvent.id} " +
                    "and status: ${previousEvent.status}. The current event's status is ${currentEvent.uptimeStatus}."
            )
            if (currentEvent.statusNotEquals(previousEvent)) {
                logger.debug(
                    "[${monitor.name}] The status of the previous event is different from the " +
                        "current event. Ending the previous event and inserting a new one."
                )
                dslContext.transactionResult { config ->
                    val txCtx = config.dsl()
                    endUptimeEvent(previousEvent, currentEvent, txCtx)
                    insertUptimeEvent(currentEvent, txCtx)
                }
                logger.debug(
                    "[${monitor.name}] The previous event has been ended and a new one " +
                        "has been inserted."
                )
            } else {
                logger.debug(
                    "[${monitor.name}] The status of the previous event is the same as the current " +
                        "event. Updating the updatedAt timestamp of the previous event."
                )
                updateEvent(currentEvent, previousEvent)
            }
        } ?: run {
            logger.debug("A previous event was not found for [${monitor.name}], creating a new one")
            insertUptimeEvent(currentEvent)
        }
    }

    private fun endUptimeEvent(
        previousEvent: UptimeEventRecord,
        currentEvent: UptimeMonitorEvent,
        txCtx: DSLContext
    ) =
        when (currentEvent) {
            is HttpUptimeMonitorEvent -> httpUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )

            is PushUptimeMonitorEvent -> pushUptimeEventRepository.endEventById(
                eventId = previousEvent.id,
                endedAt = currentEvent.dispatchedAt,
                ctx = txCtx,
            )
        }

    private fun insertUptimeEvent(currentEvent: UptimeMonitorEvent, txCtx: DSLContext? = null) =
        when (currentEvent) {
            is HttpUptimeMonitorEvent -> httpUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx)
            is PushUptimeMonitorEvent -> pushUptimeEventRepository.insertFromMonitorEvent(currentEvent, txCtx)
        }

    private fun updateEvent(currentEvent: UptimeMonitorEvent, previousEvent: UptimeEventRecord) =
        when (currentEvent) {
            is HttpUptimeMonitorEvent -> httpUptimeEventRepository.updateEvent(previousEvent.id, currentEvent)
            is PushUptimeMonitorEvent -> pushUptimeEventRepository.updateEvent(previousEvent.id, currentEvent)
        }

    fun handleSSLMonitorEvent(currentEvent: SSLMonitorEvent) {
        currentEvent.previousEvent?.let { previousEvent ->
            if (currentEvent.statusNotEquals(previousEvent)) {
                dslContext.transaction { config ->
                    sslEventRepository.endEventById(previousEvent.id, currentEvent.dispatchedAt, config.dsl())
                    sslEventRepository.insertFromMonitorEvent(currentEvent, config.dsl())
                }
            } else {
                sslEventRepository.updateEvent(previousEvent.id, currentEvent)
            }
        } ?: sslEventRepository.insertFromMonitorEvent(currentEvent)
    }
}
