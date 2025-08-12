package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import jakarta.inject.Singleton

@Singleton
class NoOpUpDispatcher(
    private val eventDispatcher: EventDispatcher,
    uptimeEventRepository: UptimeEventRepository,
) : HttpResponseChecker(eventDispatcher, uptimeEventRepository) {

    /**
     * Dispatches simply a [MonitorUpEvent] without any checks, it's intended to be used at the end of the check
     * pipeline when all checks have passed.
     */
    override fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult.Finished {
        eventDispatcher.dispatch(
            MonitorUpEvent(
                monitor = ctx.monitor,
                status = ctx.response.httpResponse.status,
                latency = ctx.response.latency,
                previousEvent = getPreviousEvent(ctx.monitor.id),
            )
        )
        return HttpCheckResult.Finished
    }
}
