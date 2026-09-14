package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.enums.HttpMethod
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.http.requestHeadersAsMap
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpRequest
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
     * @return A configured [io.micronaut.http.MutableHttpRequest].
     */
    fun fromMonitor(monitor: HttpMonitorRecord, uri: URI): MutableHttpRequest<*> =
        provisionRequestWithMethodAndBody(monitor.requestMethod, uri, monitor.requestBody)
            .initializeHeaders()
            .decorateWithHeaders(monitor, uri)

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

    private fun URI.hasSameOriginAs(other: URI): Boolean =
        scheme.equals(other.scheme, ignoreCase = true) &&
            originAuthority?.equals(other.originAuthority, ignoreCase = true) == true

    // URI.host is null for hosts that can't be parsed (e.g. the ones containing an underscore), so the raw authority
    // is compared for them instead, otherwise two different unparsable hosts would be treated as the same origin
    private val URI.originAuthority: String?
        get() = host?.let { "$it:$effectivePort" } ?: authority

    private val URI.effectivePort: Int
        get() = when {
            port != -1 -> port
            scheme.equals("https", ignoreCase = true) -> DEFAULT_HTTPS_PORT
            else -> DEFAULT_HTTP_PORT
        }

    companion object {
        private val logger = loggerFor<HttpCheckRequestConfigurator>()
        const val USER_AGENT = "Kuvasz Uptime Checker/2 https://github.com/kuvasz-uptime/kuvasz"
        private const val FALLBACK_EMPTY_BODY = "{}"
        private const val DEFAULT_HTTP_PORT = 80
        private const val DEFAULT_HTTPS_PORT = 443
    }
}
