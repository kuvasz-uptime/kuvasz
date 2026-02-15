package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class HttpCheckResponseEvaluator(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: HttpUptimeEventRepository,
    private val responseStatusChecker: HttpResponseStatusChecker,
    private val responseTimeChecker: HttpResponseTimeChecker,
    private val responseBodyChecker: HttpResponseBodyChecker,
    private val responseHeaderChecker: HttpResponseHeaderChecker,
    private val noOpUpDispatcher: NoOpUpDispatcher,
    private val databaseEventHandler: DatabaseEventHandler,
) {
    /**
     * Evaluates the HTTP response for a given monitor. The checks are intended to be chained together, allowing for an
     * early exit at any point in the evaluation process. The last check in the chain is always expected to return a
     * [com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult.Finished] result, which indicates that the evaluation
     * is complete.
     *
     * @param monitor The monitor record to evaluate the response for
     * @param response The HTTP response along with the latency data to evaluate
     * @param visitedUrls A list of the already visited URLs to track redirection loops
     *
     * @return An instance of [com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult] indicating the result of the
     * evaluation.
     */
    fun evaluateResponse(
        monitor: HttpMonitorRecord,
        response: HttpCheckResponse,
        visitedUrls: MutableList<URI>,
    ): HttpCheckResult {
        val ctx = HttpResponseCheckContext(
            monitor = monitor,
            response = response,
            visitedUrls = visitedUrls,
        )

        fun HttpCheckResult.finishOrContinueWith(checker: HttpResponseChecker): HttpCheckResult =
            when (this) {
                is HttpCheckResult.Finished, is HttpCheckResult.Redirected -> this
                is HttpCheckResult.Continue -> checker.evaluate(ctx)
            }

        return HttpCheckResult.Continue
            .finishOrContinueWith(responseStatusChecker)
            .finishOrContinueWith(responseTimeChecker)
            .finishOrContinueWith(responseHeaderChecker)
            .finishOrContinueWith(responseBodyChecker)
            // As the last step we always dispatch an UP event
            .finishOrContinueWith(noOpUpDispatcher)
    }

    /**
     * Evaluates an error that occurred during the HTTP check, by translating the passed exception into a
     * MonitorDownEvent and returning a [HttpCheckResult.Finished] result.
     *
     * @param monitor The monitor record for which the error occurred
     * @param error The exception that occurred during the HTTP check
     * @return An instance of [HttpCheckResult.Finished] indicating that the evaluation is complete.
     */
    @Suppress("TooGenericExceptionCaught")
    fun evaluateError(
        monitor: HttpMonitorRecord,
        error: Exception,
    ): HttpCheckResult.Finished {
        var clarifiedError = error
        val status = try {
            (error as? HttpClientResponseException)?.status
        } catch (ex: Exception) {
            // Invalid status codes (e.g. 498) are throwing an IllegalArgumentException for example
            // Better to have an explicit error, because the status won't be visible later, so it would be
            // harder for the users to figure out what was failing during the check
            clarifiedError = HttpClientException(ex.message, ex)
            null
        }
        HttpMonitorDownEvent(
            monitor = monitor,
            status = status,
            error = clarifiedError,
            previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)
        ).also { event ->
            val activeUptimeRecord = databaseEventHandler.handleUptimeMonitorEvent(event)
            if (monitor.failureCountThreshold <= activeUptimeRecord.failureCount) {
                eventDispatcher.dispatch(event)
            }
        }
        return HttpCheckResult.Finished
    }
}
