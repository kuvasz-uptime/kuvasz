package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.models.toMicronautHttpMethod
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.util.getRedirectionUri
import com.kuvaszuptime.kuvasz.util.isRedirected
import com.kuvaszuptime.kuvasz.util.isSuccess
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
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
import java.util.*

@Singleton
class UptimeChecker(
    @Client(configuration = HttpCheckerClientConfiguration::class)
    private val httpClient: HttpClient,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository,
) {

    companion object {
        private const val RETRY_COUNT = 3L
        private const val RETRY_INITIAL_DELAY = "1s"
        private const val RETRY_BACKOFF_MULTIPLIER = 3L
        private val logger = LoggerFactory.getLogger(UptimeChecker::class.java)
    }

    suspend fun check(
        monitor: MonitorRecord,
        uriOverride: URI? = null,
        doAfter: (monitor: MonitorRecord) -> Unit = {},
    ) {
        val previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)

        if (uriOverride == null) {
            logger.info("Starting uptime check for monitor (${monitor.name}) on URL: ${monitor.url}")
        }

        @Suppress("TooGenericExceptionCaught")
        try {
            val start = System.currentTimeMillis()
            val response = sendHttpRequest(monitor, uri = uriOverride ?: URI(monitor.url))
            val latency = (System.currentTimeMillis() - start).toInt()

            handleResponse(monitor, response, latency, previousEvent)
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
            eventDispatcher.dispatch(
                MonitorDownEvent(
                    monitor = monitor,
                    status = status,
                    error = clarifiedError,
                    previousEvent = previousEvent
                )
            )
        }
        doAfter(monitor)
    }

    // TODO handle redirect locations in a way that we pass in a list of previously
    //  seen redirects to avoid redirect loops
    private suspend fun handleResponse(
        monitor: MonitorRecord,
        response: HttpResponse<ByteBuffer<Any>>,
        latency: Int,
        previousEvent: UptimeEventRecord?
    ) {
        if (response.isSuccess()) {
            eventDispatcher.dispatch(
                MonitorUpEvent(
                    monitor = monitor,
                    status = response.status,
                    latency = latency,
                    previousEvent = previousEvent
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
                check(monitor, redirectionUri)
            } else {
                eventDispatcher.dispatch(
                    MonitorDownEvent(
                        monitor = monitor,
                        status = response.status,
                        error = Throwable(message = "Invalid redirection without a Location header"),
                        previousEvent = previousEvent
                    )
                )
            }
        } else {
            val message = if (response.isRedirected() && !monitor.followRedirects) {
                "The request was redirected, but the followRedirects option is disabled"
            } else {
                "The request wasn't successful, but there is no additional information"
            }
            eventDispatcher.dispatch(
                MonitorDownEvent(
                    monitor = monitor,
                    status = response.status,
                    error = Throwable(message),
                    previousEvent = previousEvent
                )
            )
        }
    }

    @Retryable(delay = RETRY_INITIAL_DELAY, attempts = "$RETRY_COUNT", multiplier = "$RETRY_BACKOFF_MULTIPLIER")
    suspend fun sendHttpRequest(monitor: MonitorRecord, uri: URI): HttpResponse<ByteBuffer<Any>> {
        logger.debug("Sending HTTP request to $uri (${monitor.name})")
        val request = HttpRequest
            .create<Any>(
                monitor.requestMethod.toMicronautHttpMethod(),
                uri.toString()
            )
            .header(HttpHeaders.ACCEPT, "*/*")
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
            // TODO move it to const and make it overridable
            .header(HttpHeaders.USER_AGENT, "Kuvasz Uptime Checker/2 https://github.com/kuvasz-uptime/kuvasz")
            .apply {
                if (monitor.forceNoCache)
                    header(HttpHeaders.CACHE_CONTROL, "no-cache")
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
