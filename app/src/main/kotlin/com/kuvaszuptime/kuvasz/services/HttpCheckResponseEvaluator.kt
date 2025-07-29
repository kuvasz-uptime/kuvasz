package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.models.IneligibleStatusCodeException
import com.kuvaszuptime.kuvasz.models.InvalidRedirectionException
import com.kuvaszuptime.kuvasz.models.RedirectLoopException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResponse
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.checks.RawHttpResponse
import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.net.URI

@Singleton
class HttpCheckResponseEvaluator(
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private fun getPreviousEvent(monitor: MonitorRecord): UptimeEventRecord? =
        uptimeEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)

    /**
     * Evaluates the HTTP response for a given monitor. The checks are intended to be chained together, allowing for an
     * early exit at any point in the evaluation process. The last check in the chain is always expected to return a
     * [HttpCheckResult.Finished] result, which indicates that the evaluation is complete.
     *
     * @param monitor The monitor record to evaluate the response for
     * @param response The HTTP response along with the latency data to evaluate
     * @param visitedUrls A list of the already visited URLs to track redirection loops
     *
     * @return An instance of [HttpCheckResult] indicating the result of the evaluation.
     */
    fun evaluateResponse(
        monitor: MonitorRecord,
        response: HttpCheckResponse,
        visitedUrls: MutableList<URI>,
    ): HttpCheckResult =
        checkStatusAndRedirection(monitor, response, visitedUrls)
            .finishOrContinueWith { dispatchUpEvent(monitor, response) }

    /**
     * A helper function to finish or continue the evaluation based on the previous [HttpCheckResult], which allows
     * for chaining checks without unnecessary nesting.
     */
    private inline fun HttpCheckResult.finishOrContinueWith(block: () -> HttpCheckResult): HttpCheckResult =
        when (this) {
            is HttpCheckResult.Finished, is HttpCheckResult.Redirected -> this
            is HttpCheckResult.Continue -> block()
        }

    /**
     * Checks the HTTP response status and handles redirection if necessary.
     * First it checks if the response is successful (2xx status code).
     * If the response is a redirect and the monitor is configured to follow redirects, it will return the details
     * that are necessary to follow the redirect.
     * If the response is not successful and not a redirect, it will dispatch a down event with the error.
     */
    private fun checkStatusAndRedirection(
        monitor: MonitorRecord,
        response: HttpCheckResponse,
        visitedUrls: MutableList<URI>,
    ): HttpCheckResult =
        if (response.httpResponse.isSuccess()) {
            logger.debug(
                "Status check passed for monitor with ID: ${monitor.id} with status ${response.httpResponse.status}"
            )
            HttpCheckResult.Continue
        } else if (response.httpResponse.isRedirected() && monitor.followRedirects) {
            logger.debug(
                "A redirect was detected for monitor with ID: ${monitor.id} " +
                    "with status ${response.httpResponse.status}, following to new location"
            )
            checkRedirection(monitor, response.httpResponse, visitedUrls)
        } else {
            logger.debug(
                "Status check failed for monitor with ID: ${monitor.id} with status ${response.httpResponse.status}"
            )
            val error = if (response.httpResponse.isRedirected() && !monitor.followRedirects) {
                InvalidRedirectionException("The request was redirected, but the followRedirects option is disabled")
            } else {
                IneligibleStatusCodeException(response.httpResponse.status.code)
            }
            dispatchDownEvent(monitor, response.httpResponse.status, error)
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
        monitor: MonitorRecord,
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
        return dispatchDownEvent(monitor, status, clarifiedError)
    }

    /**
     * Checks if the response is a redirect and dispatches a RedirectEvent if it is.
     * It is also responsible for checking if the redirection URI has already been visited to prevent redirect loops.
     * If the redirection URI is valid and not visited, it returns a [HttpCheckResult.Redirected] result.
     */
    private fun checkRedirection(
        monitor: MonitorRecord,
        response: RawHttpResponse,
        visitedUrls: MutableList<URI>,
    ): HttpCheckResult {
        val redirectionUri = response.getRedirectionUri(originalUrl = monitor.url)

        return if (redirectionUri != null) {
            eventDispatcher.dispatch(
                RedirectEvent(
                    monitor = monitor,
                    redirectLocation = redirectionUri
                )
            )
            if (visitedUrls.contains(redirectionUri)) {
                // If the redirection URI has already been visited, we have a redirect loop
                dispatchDownEvent(monitor = monitor, status = response.status, error = RedirectLoopException())
            } else {
                HttpCheckResult.Redirected(redirectionUri, visitedUrls)
            }
        } else {
            // If the response is a redirect but does not contain a Location header, we cannot follow it
            dispatchDownEvent(
                monitor = monitor,
                status = response.status,
                error = InvalidRedirectionException("Invalid redirection without a Location header")
            )
        }
    }

    private fun dispatchDownEvent(
        monitor: MonitorRecord,
        status: HttpStatus?,
        error: Exception,
    ): HttpCheckResult.Finished {
        eventDispatcher.dispatch(
            MonitorDownEvent(
                monitor = monitor,
                status = status,
                error = error,
                previousEvent = getPreviousEvent(monitor)
            )
        )
        return HttpCheckResult.Finished
    }

    private fun dispatchUpEvent(
        monitor: MonitorRecord,
        response: HttpCheckResponse,
    ): HttpCheckResult.Finished {
        eventDispatcher.dispatch(
            MonitorUpEvent(
                monitor = monitor,
                status = response.httpResponse.status,
                latency = response.latency,
                previousEvent = getPreviousEvent(monitor),
            )
        )
        return HttpCheckResult.Finished
    }

    @Suppress("MagicNumber")
    private fun HttpResponse<*>.isSuccess(): Boolean = this.status.code in 200..299

    private fun HttpResponse<*>.isRedirected(): Boolean =
        listOf(
            HttpStatus.MOVED_PERMANENTLY,
            HttpStatus.FOUND,
            HttpStatus.SEE_OTHER,
            HttpStatus.TEMPORARY_REDIRECT,
            HttpStatus.PERMANENT_REDIRECT
        ).contains(this.status)

    private fun HttpResponse<*>.getRedirectionUri(originalUrl: String): URI? =
        if (isRedirected()) {
            header(HttpHeaders.LOCATION)
                ?.let { locationHeader ->
                    // If the location header starts with "http", it's probably an absolute URL, we can use it as is
                    if (locationHeader.startsWith("http")) {
                        locationHeader.toUri()
                    } else {
                        // Otherwise, we need to resolve it against the original URL as a relative path
                        URI(originalUrl).resolve(locationHeader)
                    }
                }
        } else {
            null
        }
}
