package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.models.toMicronautHttpMethod
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.tables.records.UptimeEventRecord
import com.kuvaszuptime.kuvasz.util.RawHttpResponse
import com.kuvaszuptime.kuvasz.util.getRedirectionUri
import com.kuvaszuptime.kuvasz.util.isRedirected
import com.kuvaszuptime.kuvasz.util.isSuccess
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.rxjava3.http.client.Rx3HttpClient
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class UptimeChecker(
    @Client(configuration = HttpCheckerClientConfiguration::class)
    private val httpClient: Rx3HttpClient,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository
) {

    companion object {
        private const val RETRY_COUNT = 3L
    }

    fun check(monitor: MonitorRecord, uriOverride: URI? = null) {
        val previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)
        var start = 0L

        sendHttpRequest(monitor, uri = uriOverride ?: URI(monitor.url))
            .doOnSubscribe { start = System.currentTimeMillis() }
            .subscribe(
                { response -> handleResponse(monitor, response, start, previousEvent) },
                { error ->
                    eventDispatcher.dispatch(
                        MonitorDownEvent(
                            monitor = monitor,
                            status = (error as? HttpClientResponseException)?.status,
                            error = error,
                            previousEvent = previousEvent
                        )
                    )
                }
            )
    }

    private fun handleResponse(
        monitor: MonitorRecord,
        response: HttpResponse<ByteBuffer<Any>>,
        start: Long,
        previousEvent: UptimeEventRecord?
    ) {
        if (response.isSuccess()) {
            val latency = (System.currentTimeMillis() - start).toInt()
            eventDispatcher.dispatch(
                MonitorUpEvent(
                    monitor = monitor,
                    status = response.status,
                    latency = latency,
                    previousEvent = previousEvent
                )
            )
        } else if (response.isRedirected() && monitor.followRedirects) {
            val redirectionUri = response.getRedirectionUri()
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

    private fun sendHttpRequest(monitor: MonitorRecord, uri: URI): RawHttpResponse {
        val request = HttpRequest
            .create<Any>(
                monitor.requestMethod.toMicronautHttpMethod(),
                uri.toString()
            )
            .header(HttpHeaders.ACCEPT, "*/*")
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
            .apply {
                if (monitor.forceNoCache)
                    header(HttpHeaders.CACHE_CONTROL, "no-cache")
            }

        return httpClient.exchange(request).retry(RETRY_COUNT)
    }
}

@Singleton
class HttpCheckerClientConfiguration(config: ApplicationConfiguration) : HttpClientConfiguration(config) {

    override fun getEventLoopGroup(): String = EVENT_LOOP_GROUP

    override fun isFollowRedirects(): Boolean = false

    override fun getConnectionPoolConfiguration(): ConnectionPoolConfiguration = ConnectionPoolConfiguration()

    companion object {
        private const val EVENT_LOOP_GROUP = "uptime-check"
    }
}
