package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.models.ResponseTimeThresholdExceededException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton

@Singleton
class HttpResponseTimeChecker(
    eventDispatcher: EventDispatcher,
    uptimeEventRepository: HttpUptimeEventRepository,
    databaseEventHandler: DatabaseEventHandler,
    pendingFailureRepository: PendingFailureRepository,
) : HttpResponseChecker(eventDispatcher, uptimeEventRepository, databaseEventHandler, pendingFailureRepository) {

    companion object {
        private val logger = loggerFor<HttpResponseTimeChecker>()
    }

    /**
     * Checks the response time against the configured threshold for the monitor.
     * If the response time exceeds the threshold, it dispatches a down event with the error
     * and returns a [HttpCheckResult.Finished] result.
     * If the response time is within the threshold, it returns a [HttpCheckResult.Continue] result.
     */
    override fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult {
        val response = ctx.response
        val monitor = ctx.monitor
        val threshold = monitor.responseTimeThresholdMillis ?: return HttpCheckResult.Continue
        val responseTime = response.latency

        return if (responseTime > threshold) {
            logger.debug(
                "Response time check failed for monitor with ID: ${monitor.id}, " +
                    "response time: $responseTime ms, threshold: $threshold ms"
            )
            dispatchDownEvent(
                ctx,
                error = ResponseTimeThresholdExceededException(
                    responseTimeMillis = responseTime,
                    thresholdMillis = threshold
                )
            )
        } else {
            logger.debug(
                "Response time check passed for monitor with ID: ${monitor.id}, " +
                    "response time: $responseTime ms, threshold: $threshold ms"
            )
            HttpCheckResult.Continue
        }
    }
}
