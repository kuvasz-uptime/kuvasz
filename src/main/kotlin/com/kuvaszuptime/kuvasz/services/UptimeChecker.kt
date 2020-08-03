package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.events.RedirectEvent
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.util.RawHttpResponse
import com.kuvaszuptime.kuvasz.util.getRedirectionUri
import com.kuvaszuptime.kuvasz.util.isSuccess
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UptimeChecker @Inject constructor(
    private val httpClient: RxHttpClient,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: UptimeEventRepository
) {

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
                        response.getRedirectionUri().fold(
                            {
                                eventDispatcher.dispatch(
                                    MonitorDownEvent(
                                        monitor = monitor,
                                        status = response.status,
                                        error = Throwable(message = "Unknown error occurred"),
                                        previousEvent = previousEvent
                                    )
                                )
                            },
                            { redirectionUri ->
                                eventDispatcher.dispatch(
                                    RedirectEvent(
                                        monitor = monitor,
                                        redirectLocation = redirectionUri
                                    )
                                )
                                check(monitor, redirectionUri)
                            }
                        )
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
        // TODO revise headers
        val request = HttpRequest.GET<Any>(uri)
            .header(HttpHeaders.ACCEPT, "*/*")
            .header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
            .header(HttpHeaders.CACHE_CONTROL, "no-cache")

        return httpClient.exchange(request)
    }
}
