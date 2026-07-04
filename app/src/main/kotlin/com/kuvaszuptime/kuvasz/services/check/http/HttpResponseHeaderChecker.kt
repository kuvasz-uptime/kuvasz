package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.models.ExpectedHeaderNotFoundException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.monitor.http.expectedHeadersAsMap
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.loggerFor
import jakarta.inject.Singleton

@Singleton
class HttpResponseHeaderChecker(
    eventDispatcher: EventDispatcher,
    uptimeEventRepository: HttpUptimeEventRepository,
    databaseEventHandler: DatabaseEventHandler,
    pendingFailureRepository: PendingFailureRepository,
) : HttpResponseChecker(eventDispatcher, uptimeEventRepository, databaseEventHandler, pendingFailureRepository) {

    companion object {
        private val logger = loggerFor<HttpResponseHeaderChecker>()
    }

    /**
     * Checks the response's headers against the pre-configured, expected headers on the monitor. The check is
     * case-insensitive, but the value should match exactly (excluding whitespaces and the case of course).
     */
    override fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult {
        val monitor = ctx.monitor
        val expectedHeaders = monitor.expectedHeadersAsMap().ifEmpty { null } ?: return HttpCheckResult.Continue
        logger.debug("Checking response headers against expected headers: $expectedHeaders")
        val response = ctx.response
        val responseHeaders = response.httpResponse.headers

        val failingHeaders = mutableListOf<String>()
        expectedHeaders.forEach { expectedHeader ->
            val actualHeaderValue = responseHeaders.get(expectedHeader.key.trim())?.trim()
            if (actualHeaderValue == null || actualHeaderValue != expectedHeader.value.trim()) {
                failingHeaders.add(expectedHeader.key)
            }
        }

        return if (failingHeaders.isEmpty()) {
            HttpCheckResult.Continue
        } else {
            val error = ExpectedHeaderNotFoundException(failingHeaders)
            logger.debug(error.message)
            dispatchDownEvent(ctx, error)
        }
    }
}
