package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class HttpCheckRequestConfigurator {

    /**
     * Creates a [io.micronaut.http.MutableHttpRequest] from the given
     * [com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord] and [java.net.URI].
     *
     * @param monitor The monitor record containing request details.
     * @param uri The URI to which the request will be sent. Because of possible redirects,
     * this URI may differ from the one stored in the monitor.
     * @return A configured [io.micronaut.http.MutableHttpRequest].
     */
    fun fromMonitor(monitor: MonitorRecord, uri: URI): MutableHttpRequest<*> = HttpRequest
        .create<String>(
            monitor.requestMethod.toMicronautHttpMethod(),
            uri.toString()
        )
        .initializeHeaders()
        .decorateWithHeaders(monitor)

    /**
     * Initializes the common headers for the HTTP request.
     */
    private fun MutableHttpRequest<*>.initializeHeaders(): MutableHttpRequest<*> = this
        .header(HttpHeaders.ACCEPT, "*/*")
        .header(HttpHeaders.USER_AGENT, USER_AGENT)

    /**
     * Applies additional headers based on the monitor's configuration.
     */
    private fun MutableHttpRequest<*>.decorateWithHeaders(monitor: MonitorRecord): MutableHttpRequest<*> =
        this.apply {
            if (monitor.forceNoCache) {
                header(HttpHeaders.CACHE_CONTROL, "no-cache")
            }
            // If we don't want to check the response body, or it's a HEAD request, we can accept compressed responses
            // to save bandwidth and speed up the request
            if (monitor.expectedKeyword.isNullOrEmpty()) {
                header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
            }
        }

    private fun HttpMethod.toMicronautHttpMethod(): io.micronaut.http.HttpMethod {
        return when (this) {
            HttpMethod.GET -> io.micronaut.http.HttpMethod.GET
            HttpMethod.HEAD -> io.micronaut.http.HttpMethod.HEAD
        }
    }

    companion object {
        const val USER_AGENT = "Kuvasz Uptime Checker/2 https://github.com/kuvasz-uptime/kuvasz"
    }
}
