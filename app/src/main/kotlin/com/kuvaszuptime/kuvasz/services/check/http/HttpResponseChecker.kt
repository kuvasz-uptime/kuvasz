package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import java.net.URI

abstract class HttpResponseChecker(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository,
) {

    abstract fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult

    protected fun getPreviousEvent(monitorId: Long): UptimeEventRecord? =
        uptimeEventRepository.getPreviousEventByMonitorId(monitorId)

    protected fun dispatchDownEvent(ctx: HttpResponseCheckContext, error: Exception): HttpCheckResult.Finished {
        eventDispatcher.dispatch(
            MonitorDownEvent(
                monitor = ctx.monitor,
                status = ctx.response.httpResponse.status,
                error = error,
                previousEvent = getPreviousEvent(ctx.monitor.id)
            )
        )
        return HttpCheckResult.Finished
    }
}

data class HttpResponseCheckContext(
    val monitor: MonitorRecord,
    val response: HttpCheckResponse,
    val visitedUrls: MutableList<URI>,
)
