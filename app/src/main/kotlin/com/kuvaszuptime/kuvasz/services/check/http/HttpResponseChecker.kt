package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import java.net.URI

abstract class HttpResponseChecker(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val databaseEventHandler: DatabaseEventHandler,
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
            val activeUptimeRecord = databaseEventHandler.handleUptimeMonitorEvent(event)
            if (ctx.monitor.failureCountThreshold <= activeUptimeRecord.failureCount) {
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
)
