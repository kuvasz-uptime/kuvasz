package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.check.isDownNow
import java.net.URI

abstract class HttpResponseChecker(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val databaseEventHandler: DatabaseEventHandler,
    private val pendingFailureRepository: PendingFailureRepository,
) {

    abstract fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult

    protected fun getPreviousEvent(monitorId: Long): HttpUptimeEventRecord? =
        uptimeEventRepository.getPreviousEventByMonitorId(monitorId)

    protected fun dispatchDownEvent(ctx: HttpResponseCheckContext, error: Exception): HttpCheckResult.Finished {
        HttpMonitorDownEvent(
            monitor = ctx.monitor,
            status = ctx.response.httpResponse.status,
            error = error,
            previousEvent = getPreviousEvent(ctx.monitor.id)
        ).also { event ->
            if (event.isDownNow(pendingFailureRepository)) {
                databaseEventHandler.handleUptimeMonitorEvent(event)
                eventDispatcher.dispatch(event)
            }
        }
        return HttpCheckResult.Finished
    }
}

data class HttpResponseCheckContext(
    val monitor: HttpMonitorRecord,
    val response: HttpCheckResponse,
    val visitedUrls: MutableList<URI>,
) {
    /**
     * The URI that actually produced [response]. The checker appends every hop to [visitedUrls] right before it
     * sends the request, so the last entry is the current one; it falls back to the monitor's own URL when no hop
     * has been recorded yet. A relative Location header has to be resolved against this and not against
     * [monitor]'s URL, otherwise a later hop could silently retarget the request to an unrelated host.
     */
    val currentUri: URI get() = visitedUrls.lastOrNull() ?: URI(monitor.url)
}
