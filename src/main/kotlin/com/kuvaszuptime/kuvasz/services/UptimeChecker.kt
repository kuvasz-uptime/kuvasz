package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.models.toMicronautHttpMethod
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.RawHttpResponse
import com.kuvaszuptime.kuvasz.util.getRedirectionUri
import com.kuvaszuptime.kuvasz.util.isRedirected
import com.kuvaszuptime.kuvasz.util.isSuccess
import io.micronaut.context.annotation.Factory
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.rxjava3.http.client.Rx3HttpClient
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class UptimeChecker(
    @Client("uptime-checker") private val httpClient: Rx3HttpClient,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository
) {

    companion object {
        private const val RETRY_COUNT = 3L
    }

    fun check(monitor: MonitorPojo, uriOverride: URI? = null) {
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
        monitor: MonitorPojo,
        response: HttpResponse<ByteBuffer<Any>>,
        start: Long,
        previousEvent: UptimeEventPojo?
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

    private fun sendHttpRequest(monitor: MonitorPojo, uri: URI): RawHttpResponse {
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

@Factory
class UptimeCheckerHttpClientConfigFactory {

    @Named("uptime-checker")
    @Singleton
    fun configuration(): HttpClientConfiguration {
        val config = DefaultHttpClientConfiguration()
        config.eventLoopGroup = "uptime-check"
        config.isFollowRedirects = false
        return config
    }
}
