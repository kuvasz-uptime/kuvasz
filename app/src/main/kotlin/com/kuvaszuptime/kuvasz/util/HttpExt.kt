package com.kuvaszuptime.kuvasz.util

import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.views.htmx.http.HtmxRequestHeaders
import java.net.URI
import kotlin.jvm.optionals.getOrNull

typealias RawHttpResponse = HttpResponse<ByteBuffer<Any>>

@Suppress("MagicNumber")
fun HttpResponse<*>.isSuccess(): Boolean = this.status.code in 200..299

fun HttpResponse<*>.isRedirected(): Boolean =
    listOf(
        HttpStatus.MOVED_PERMANENTLY,
        HttpStatus.FOUND,
        HttpStatus.SEE_OTHER,
        HttpStatus.TEMPORARY_REDIRECT,
        HttpStatus.PERMANENT_REDIRECT
    ).contains(this.status)

fun String.toUri(): URI = URI(this)

fun HttpResponse<*>.getRedirectionUri(originalUrl: String): URI? =
    if (isRedirected()) {
        header(HttpHeaders.LOCATION)
            ?.let { locationHeader ->
                // If the location header starts with "http", it's probably an absolute URL, we can use it as is
                if (locationHeader.startsWith("http")) {
                    locationHeader.toUri()
                } else {
                    // Otherwise, we need to resolve it against the original URL as a relative path
                    URI(originalUrl).resolve(locationHeader)
                }
            }
    } else {
        null
    }

fun HttpRequest<*>.isHtmxRequest(): Boolean = this.headers.contains(HtmxRequestHeaders.HX_REQUEST)

inline fun <reified T : Any> HttpResponse<*>.getBodyAs(): T? = getBody(T::class.java).getOrNull()
