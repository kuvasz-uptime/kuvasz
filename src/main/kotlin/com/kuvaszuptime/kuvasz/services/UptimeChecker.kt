package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.models.toMicronautHttpMethod
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.util.RawHttpResponse
import com.kuvaszuptime.kuvasz.util.getRedirectionUri
import com.kuvaszuptime.kuvasz.util.isRedirected
import com.kuvaszuptime.kuvasz.util.isSuccess
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.retry.annotation.Retryable
import io.micronaut.runtime.ApplicationConfiguration
import jakarta.inject.Singleton
import kotlinx.coroutines.reactive.awaitSingle
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.util.Optional

@Singleton
class UptimeChecker(
    @Client(configuration = HttpCheckerClientConfiguration::class)
    private val httpClient: HttpClient,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository,
    private val monitorRepository: MonitorRepository,
) {

    companion object {
        private const val RETRY_COUNT = 2L
        private const val RETRY_INITIAL_DELAY = "500ms"
        private const val RETRY_BACKOFF_MULTIPLIER = 3L
        const val USER_AGENT = "Kuvasz Uptime Checker/2 https://github.com/kuvasz-uptime/kuvasz"
        private val logger = LoggerFactory.getLogger(UptimeChecker::class.java)
    }

    private fun getPreviousEvent(monitor: MonitorRecord): UptimeEventRecord? =
        uptimeEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)

    suspend fun check(
        monitor: MonitorRecord,
        uriOverride: URI? = null,
        visitedUrls: MutableList<URI> = mutableListOf(),
        doAfter: ((monitor: MonitorRecord) -> Unit)? = null,
    ) {
        if (uriOverride == null) {
            logger.info("Starting uptime check for monitor (${monitor.name}) on URL: ${monitor.url}")
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            val effectiveUrl = uriOverride ?: URI(monitor.url)
            visitedUrls.add(effectiveUrl)

            val start = System.currentTimeMillis()
            val response = sendHttpRequest(monitor, uri = effectiveUrl)
            val latency = (System.currentTimeMillis() - start).toInt()

            handleResponse(monitor, response, latency, visitedUrls)
        } catch (error: Throwable) {
            var clarifiedError = error
            val status = try {
                (error as? HttpClientResponseException)?.status
            } catch (ex: Throwable) {
                // Invalid status codes (e.g. 498) are throwing an IllegalArgumentException for example
                // Better to have an explicit error, because the status won't be visible later, so it would be
                // harder for the users to figure out what was failing during the check
                clarifiedError = HttpClientException(ex.message, ex)
                null
            }
            dispatchDownEvent(monitor, status, clarifiedError)
        }
        logger.debug("Uptime check for monitor (${monitor.name}) finished")
        if (doAfter != null) {
            monitorRepository.findById(monitor.id)?.let { upToDateMonitor ->
                logger.debug("Calling doAfter() hook on monitor with name [${upToDateMonitor.name}]")
                doAfter(upToDateMonitor)
            }
        }
    }

    private suspend fun handleResponse(
        monitor: MonitorRecord,
        response: RawHttpResponse,
        latency: Int,
        visitedUrls: MutableList<URI>,
    ) {
        if (response.isSuccess()) {
            eventDispatcher.dispatch(
                MonitorUpEvent(
                    monitor = monitor,
                    status = response.status,
                    latency = latency,
                    previousEvent = getPreviousEvent(monitor),
                )
            )
        } else if (response.isRedirected() && monitor.followRedirects) {
            val redirectionUri = response.getRedirectionUri(originalUrl = monitor.url)
            if (redirectionUri != null) {
                eventDispatcher.dispatch(
                    RedirectEvent(
                        monitor = monitor,
                        redirectLocation = redirectionUri
                    )
                )
                if (visitedUrls.contains(redirectionUri)) {
                    dispatchDownEvent(monitor, response.status, Throwable("Redirect loop detected"))
                    return
                }
                check(monitor, redirectionUri, visitedUrls)
            } else {
                dispatchDownEvent(monitor, response.status, Throwable("Invalid redirection without a Location header"))
            }
        } else {
            val message = if (response.isRedirected() && !monitor.followRedirects) {
                "The request was redirected, but the followRedirects option is disabled"
            } else {
                "The request wasn't successful, but there is no additional information"
            }
            dispatchDownEvent(monitor, response.status, Throwable(message))
        }
    }

    private fun dispatchDownEvent(
        monitor: MonitorRecord,
        status: HttpStatus?,
        error: Throwable,
    ) {
        eventDispatcher.dispatch(
            MonitorDownEvent(
                monitor = monitor,
                status = status,
                error = error,
                previousEvent = getPreviousEvent(monitor)
            )
        )
    }

    @Retryable(
        delay = RETRY_INITIAL_DELAY,
        attempts = "$RETRY_COUNT",
        multiplier = "$RETRY_BACKOFF_MULTIPLIER",
    )
    suspend fun sendHttpRequest(monitor: MonitorRecord, uri: URI): RawHttpResponse {
        logger.debug("Sending HTTP request to $uri (${monitor.name})")
        val request = HttpRequest
            .create<Any>(
                monitor.requestMethod.toMicronautHttpMethod(),
                uri.toString()
            )
            .header(HttpHeaders.ACCEPT, "*/*")
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
            .header(HttpHeaders.USER_AGENT, USER_AGENT)
            .apply {
                if (monitor.forceNoCache) {
                    header(HttpHeaders.CACHE_CONTROL, "no-cache")
                }
            }

        return httpClient.exchange(request).awaitSingle()
    }
}

@Singleton
class HttpCheckerClientConfiguration(config: ApplicationConfiguration) : HttpClientConfiguration(config) {

    override fun getEventLoopGroup(): String = EVENT_LOOP_GROUP

    override fun isFollowRedirects(): Boolean = false

    override fun getReadTimeout(): Optional<Duration> = Optional.of(Duration.ofSeconds(READ_TIMEOUT_SECONDS))

    override fun getConnectionPoolConfiguration(): ConnectionPoolConfiguration = ConnectionPoolConfiguration()

    companion object {
        private const val EVENT_LOOP_GROUP = "uptime-check"
        private const val READ_TIMEOUT_SECONDS = 30L
    }
}
