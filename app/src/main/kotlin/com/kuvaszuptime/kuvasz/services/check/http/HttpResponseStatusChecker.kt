package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.IneligibleStatusCodeException
import com.kuvaszuptime.kuvasz.models.InvalidRedirectionException
import com.kuvaszuptime.kuvasz.models.RedirectLoopException
import com.kuvaszuptime.kuvasz.models.TooManyRedirectsException
import com.kuvaszuptime.kuvasz.models.checks.HttpCheckResult
import com.kuvaszuptime.kuvasz.models.checks.RawHttpResponse
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.repositories.HttpUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.isSuccess
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class HttpResponseStatusChecker(
    private val eventDispatcher: EventDispatcher,
    uptimeEventRepository: HttpUptimeEventRepository,
    databaseEventHandler: DatabaseEventHandler,
    pendingFailureRepository: PendingFailureRepository,
    private val appConfig: AppConfig,
) : HttpResponseChecker(eventDispatcher, uptimeEventRepository, databaseEventHandler, pendingFailureRepository) {

    companion object {
        private val logger = loggerFor<HttpResponseStatusChecker>()
    }

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
     * It is also responsible for capping the length of the redirect chain, and for checking if the redirection URI
     * has already been visited to prevent redirect loops.
     * If the redirection URI is valid and not visited, it returns a [HttpCheckResult.Redirected] result.
     */
    private fun checkRedirection(ctx: HttpResponseCheckContext): HttpCheckResult {
        val maxRedirects = appConfig.httpCheckMaxRedirects
        // visitedUrls already contains the current hop, so its size is the number of the redirect we are about to
        // follow. Chains that never repeat a URI are not caught by the loop detection below, so they need this cap.
        if (ctx.visitedUrls.size > maxRedirects) {
            return dispatchDownEvent(ctx, TooManyRedirectsException(maxRedirects))
        }
        val redirectionUri = ctx.response.httpResponse.getRedirectionUri(currentUri = ctx.currentUri)

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

    private fun HttpResponse<*>.getRedirectionUri(currentUri: URI): URI? =
        header(HttpHeaders.LOCATION)?.let { locationHeader ->
            // Absolute locations are returned by resolve() as they are, relative ones are resolved against the URL of
            // the hop that returned them
            currentUri.resolve(locationHeader)
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
