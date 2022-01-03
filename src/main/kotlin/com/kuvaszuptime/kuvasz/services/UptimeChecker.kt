package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.util.RawHttpResponse
import com.kuvaszuptime.kuvasz.util.getRedirectionUri
import com.kuvaszuptime.kuvasz.util.isSuccess
import io.micronaut.context.annotation.Factory
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.rxjava2.http.client.RxHttpClient
import jakarta.inject.Named
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class UptimeChecker(
    @Client("uptime-checker") private val httpClient: RxHttpClient,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository
) {

    companion object {
        private const val RETRY_COUNT = 3L
    }

    fun check(monitor: MonitorPojo, uriOverride: URI? = null) {
        val previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)
        var start = 0L

        sendHttpRequest(uri = uriOverride ?: URI(monitor.url))
            .doOnSubscribe { start = System.currentTimeMillis() }
            .subscribe(
                { response ->
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
                    } else {
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
                                    error = Throwable(message = "Unknown error occurred"),
                                    previousEvent = previousEvent
                                )
                            )
                        }
                    }
                },
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

    private fun sendHttpRequest(uri: URI): RawHttpResponse {
        val request = HttpRequest.GET<Any>(uri)
            .header(HttpHeaders.ACCEPT, "*/*")
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")

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
        return config
    }
}
