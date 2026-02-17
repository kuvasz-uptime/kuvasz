package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import jakarta.inject.Singleton

@Singleton
class NoOpUpDispatcher(
    private val eventDispatcher: EventDispatcher,
    uptimeEventRepository: HttpUptimeEventRepository,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val pendingFailureRepository: PendingFailureRepository,
) : HttpResponseChecker(eventDispatcher, uptimeEventRepository, databaseEventHandler, pendingFailureRepository) {

    /**
     * Dispatches simply a [HttpMonitorUpEvent] without any checks, it's intended to be used at the end of the check
     * pipeline when all checks have passed.
     */
    override fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult.Finished {
        if (ctx.monitor.latencyHistoryEnabled) {
            latencyLogRepository.insertLatencyForMonitor(ctx.monitor.id, ctx.response.latency)
        }

        HttpMonitorUpEvent(
            monitor = ctx.monitor,
            status = ctx.response.httpResponse.status,
            latency = ctx.response.latency,
            previousEvent = getPreviousEvent(ctx.monitor.id),
        ).also { event ->
            pendingFailureRepository.deleteByMonitorId(event.monitor.id)
            databaseEventHandler.handleUptimeMonitorEvent(event)
            eventDispatcher.dispatch(event)
        }
        return HttpCheckResult.Finished
    }
}
