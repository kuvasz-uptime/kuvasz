package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.http.requestHeadersAsMap
import com.kuvaszuptime.kuvasz.util.hasSameOriginAs
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
import io.netty.handler.codec.http.cookie.ClientCookieEncoder
import jakarta.inject.Singleton
import java.net.URI

@Singleton
class HttpCheckRequestConfigurator {

    /**
     * Creates a [io.micronaut.http.MutableHttpRequest] from the given
     * [com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord] and [java.net.URI].
     *
     * @param monitor The monitor record containing request details.
     * @param uri The URI to which the request will be sent. Because of possible redirects,
     * this URI may differ from the one stored in the monitor.
     * @param cookieJar The cookies set by the previous responses of the same check. The Cookie header of the request
     * is recorded in it as well.
     * @return A configured [io.micronaut.http.MutableHttpRequest].
     */
    fun fromMonitor(monitor: HttpMonitorRecord, uri: URI, cookieJar: HttpCheckCookieJar): MutableHttpRequest<*> {
        val cookieHeader = cookieHeaderFor(monitor, uri, cookieJar)
        cookieJar.recordRequestTo(uri, cookieHeader)
        return provisionRequestWithMethodAndBody(monitor.requestMethod, uri, monitor.requestBody)
            .initializeHeaders()
            .decorateWithHeaders(monitor, uri)
            .apply {
                if (cookieHeader != null) {
                    headers.set(HttpHeaders.COOKIE, cookieHeader)
                }
            }
    }

    /**
     * Builds the Cookie header of a request to [uri]: the cookies that were set during the same check are merged into
     * the Cookie header of the custom request headers (if it's sent to [uri] at all). If a cookie with the same name is
     * in both, the jar's one wins, because that's the most recent value set by the server. The rest of the custom
     * cookies are kept as they were configured, without parsing and re-encoding them, so even the ones that don't
     * follow RFC 6265 are sent unchanged.
     *
     * @return The value of the Cookie header, or null if there is nothing to send
     */
    fun cookieHeaderFor(monitor: HttpMonitorRecord, uri: URI, cookieJar: HttpCheckCookieJar): String? {
        val customCookieHeader = monitor.requestHeadersAsMap()
            .entries
            .firstOrNull { it.key.equals(HttpHeaders.COOKIE, ignoreCase = true) }
            ?.value
            ?.takeIf { monitor.isRequestHeaderPropagationAllowed(uri) }
        val jarCookies = cookieJar.cookiesFor(uri)
        if (jarCookies.isEmpty()) {
            return customCookieHeader
        }
        val jarCookieNames = jarCookies.map { it.name() }.toSet()
        val customCookies = customCookieHeader.orEmpty()
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.substringBefore("=").trim() !in jarCookieNames }
        val encodedJarCookies = jarCookies.map { ClientCookieEncoder.LAX.encode(it) }
        return (customCookies + encodedJarCookies).joinToString("; ")
    }

    private fun provisionRequestWithMethodAndBody(
        method: HttpMethod,
        uri: URI,
        body: String?,
    ): MutableHttpRequest<*> {
        val effectiveBody = body?.ifBlank { null } ?: FALLBACK_EMPTY_BODY
        val mediaType = MediaType.APPLICATION_JSON
        // Using application/json by default for requests with a body, currently this is the only supported content type
        val request = when (method) {
            HttpMethod.GET -> HttpRequest.GET<String>(uri)
            HttpMethod.HEAD -> HttpRequest.HEAD(uri)
            HttpMethod.DELETE -> HttpRequest.DELETE<String>(uri)
            HttpMethod.OPTIONS -> HttpRequest.OPTIONS(uri)
            HttpMethod.POST -> HttpRequest.POST(uri, effectiveBody).contentType(mediaType)
            HttpMethod.PUT -> HttpRequest.PUT(uri, effectiveBody).contentType(mediaType)
            HttpMethod.PATCH -> HttpRequest.PATCH(uri, effectiveBody).contentType(mediaType)
        }

        return request
    }

    /**
     * Initializes the common headers for the HTTP request.
     */
    private fun MutableHttpRequest<*>.initializeHeaders(): MutableHttpRequest<*> = this
        .header(HttpHeaders.ACCEPT, "*/*")
        .header(HttpHeaders.USER_AGENT, USER_AGENT)

    /**
     * Applies additional headers based on the monitor's configuration.
     */
    private fun MutableHttpRequest<*>.decorateWithHeaders(
        monitor: HttpMonitorRecord,
        uri: URI,
    ): MutableHttpRequest<*> =
        this.apply {
            if (monitor.forceNoCache) {
                header(HttpHeaders.CACHE_CONTROL, "no-cache")
            }
            // If we don't want to check the response body, or it's a HEAD request, we can accept compressed responses
            // to save bandwidth and speed up the request
            if (monitor.expectedKeyword.isNullOrEmpty()) {
                header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br")
            }
            // Adding the custom headers as a last step to make sure that they override the default ones
            if (monitor.isRequestHeaderPropagationAllowed(uri)) {
                monitor.requestHeadersAsMap().forEach { header ->
                    headers.set(header.key, header.value)
                }
            } else {
                logger.debug(
                    "Custom request headers of monitor with ID: ${monitor.id} are withheld from a different origin"
                )
            }
        }

    /**
     * The custom request headers might contain secrets, so they are only sent to an origin (scheme, host and port) that
     * differs from the monitored URL's origin (e.g. after a redirect) if the monitor explicitly allows it.
     */
    private fun HttpMonitorRecord.isRequestHeaderPropagationAllowed(uri: URI): Boolean =
        crossOriginHeaderPropagation || uri.hasSameOriginAs(URI(url))

    companion object {
        private val logger = loggerFor<HttpCheckRequestConfigurator>()
        const val USER_AGENT = "Kuvasz Uptime Checker/2 https://github.com/kuvasz-uptime/kuvasz"
        private const val FALLBACK_EMPTY_BODY = "{}"
    }
}
