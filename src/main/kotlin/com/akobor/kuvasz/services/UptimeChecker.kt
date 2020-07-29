package com.akobor.kuvasz.services

import arrow.core.Option
import com.akobor.kuvasz.events.MonitorDownEvent
import com.akobor.kuvasz.events.MonitorUpEvent
import com.akobor.kuvasz.events.RedirectEvent
import com.akobor.kuvasz.repositories.UptimeEventRepository
import com.akobor.kuvasz.tables.pojos.MonitorPojo
import com.akobor.kuvasz.tables.pojos.UptimeEventPojo
import com.akobor.kuvasz.util.getRedirectionUri
import com.akobor.kuvasz.util.isSuccess
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

    fun check(monitor: MonitorPojo) {
        val previousEvent = uptimeEventRepository.getPreviousEventByMonitorId(monitorId = monitor.id)
        sendHttpRequest(uri = URI(monitor.url), monitor = monitor, previousEvent = previousEvent)
    }

    private fun sendHttpRequest(uri: URI, monitor: MonitorPojo, previousEvent: Option<UptimeEventPojo>) {
        // TODO revise headers
        val request = HttpRequest.GET<Any>(uri)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Cache-Control", "no-cache")
        val start = System.currentTimeMillis()

        httpClient.exchange(request)
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
                                sendHttpRequest(redirectionUri, monitor, previousEvent)
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
}
