package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.IneligibleStatusCodeException
import com.kuvaszuptime.kuvasz.models.InvalidRedirectionException
import com.kuvaszuptime.kuvasz.models.RedirectLoopException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.checks.RawHttpResponse
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.isSuccess
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory
import java.net.URI

@Singleton
class HttpResponseStatusChecker(
    private val eventDispatcher: EventDispatcher,
    uptimeEventRepository: HttpUptimeEventRepository,
) : HttpResponseChecker(eventDispatcher, uptimeEventRepository) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Checks the HTTP response status and handles redirection if necessary.
     * First it checks if the response's status code is matching the expected status codes (or 2xx in case it's not
     * explicitly set).
     * If the response is a redirect and the monitor is configured to follow redirects, it will return the details
     * that are necessary to follow the redirect.
     * If the response is not successful and not a redirect, it will dispatch a down event with the error.
     */
    override fun evaluate(ctx: HttpResponseCheckContext): HttpCheckResult {
        val response = ctx.response
        val monitor = ctx.monitor

        return if (ctx.response.httpResponse.statusIsAcceptableForMonitor(monitor)) {
            logger.debug(
                "Status check passed for monitor with ID: ${monitor.id} with status ${response.httpResponse.status}"
            )
            if (response.httpResponse.isRedirected() && monitor.followRedirects) {
                logger.debug(
                    "A redirect was detected for monitor with ID: ${monitor.id} " +
                        "with status ${response.httpResponse.status}, following to new location"
                )
                checkRedirection(ctx)
            } else {
                // In case the status check passed, and it's not a redirect, or we should not follow redirects, we can
                // continue with the next checks immediately
                HttpCheckResult.Continue
            }
        } else {
            logger.debug(
                "Status check failed for monitor with ID: ${monitor.id} with status ${response.httpResponse.status}"
            )
            val error = if (response.httpResponse.isRedirected() && !monitor.followRedirects) {
                InvalidRedirectionException("The request was redirected, but following redirects is disabled")
            } else {
                IneligibleStatusCodeException(response.httpResponse.status.code)
            }
            dispatchDownEvent(ctx, error)
        }
    }

    /**
     * Checks if the response is a redirect and dispatches a RedirectEvent if it is.
     * It is also responsible for checking if the redirection URI has already been visited to prevent redirect loops.
     * If the redirection URI is valid and not visited, it returns a [HttpCheckResult.Redirected] result.
     */
    private fun checkRedirection(ctx: HttpResponseCheckContext): HttpCheckResult {
        val redirectionUri = ctx.response.httpResponse.getRedirectionUri(originalUrl = ctx.monitor.url)

        return if (redirectionUri != null) {
            eventDispatcher.dispatch(
                HttpRedirectEvent(
                    monitor = ctx.monitor,
                    redirectLocation = redirectionUri
                )
            )
            if (ctx.visitedUrls.contains(redirectionUri)) {
                // If the redirection URI has already been visited, we have a redirect loop
                dispatchDownEvent(ctx, error = RedirectLoopException())
            } else {
                HttpCheckResult.Redirected(redirectionUri, ctx.visitedUrls)
            }
        } else {
            // If the response is a redirect but does not contain a Location header, we cannot follow it
            dispatchDownEvent(ctx, InvalidRedirectionException("Invalid redirection without a Location header"))
        }
    }

    private fun HttpResponse<*>.getRedirectionUri(originalUrl: String): URI? =
        header(HttpHeaders.LOCATION)?.let { locationHeader ->
            // If the location header starts with "http", it's probably an absolute URL, we can use it as is
            if (locationHeader.startsWith("http")) {
                locationHeader.toUri()
            } else {
                // Otherwise, we need to resolve it against the original URL as a relative path
                URI(originalUrl).resolve(locationHeader)
            }
        }

    /**
     * Checks if the response status code is acceptable for the monitor with the following rules, in order:
     *
     * 1. If the monitor has an explicit list of expected status codes, it checks if the response code is in that list.
     * 2. If the monitor is configured to follow redirects, it considers 2xx and 3xx status codes as acceptable.
     * 3. If the monitor is not configured to follow redirects, it considers only 2xx status codes as acceptable.
     */
    private fun RawHttpResponse.statusIsAcceptableForMonitor(monitor: HttpMonitorRecord): Boolean =
        monitor.expectedStatusCodes.orEmpty().let { acceptedStatusCodes ->
            if (acceptedStatusCodes.isNotEmpty()) {
                // Check if the response status code is in the list of expected status codes
                acceptedStatusCodes.contains(this.status.code)
            } else if (monitor.followRedirects) {
                // If the monitor is configured to follow redirects, we consider 2xx and 3xx status codes as acceptable
                this.isRedirected() || this.isSuccess()
            } else {
                this.isSuccess()
            }
        }

    private fun HttpResponse<*>.isRedirected(): Boolean =
        listOf(
            HttpStatus.MOVED_PERMANENTLY,
            HttpStatus.FOUND,
            HttpStatus.SEE_OTHER,
            HttpStatus.NOT_MODIFIED,
            HttpStatus.TEMPORARY_REDIRECT,
            HttpStatus.PERMANENT_REDIRECT
        ).contains(this.status)
}
