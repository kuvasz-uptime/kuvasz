package com.kuvaszuptime.kuvasz.util

import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.reactivex.Flowable
import java.net.URI

typealias RawHttpResponse = Flowable<HttpResponse<ByteBuffer<Any>>>

@Suppress("MagicNumber")
fun HttpResponse<*>.isSuccess() = this.status.code in 200..299

@Suppress("MagicNumber")
fun HttpResponse<*>.isRedirected() =
    listOf(
        HttpStatus.MOVED_PERMANENTLY,
        HttpStatus.FOUND,
        HttpStatus.SEE_OTHER,
        HttpStatus.TEMPORARY_REDIRECT,
        HttpStatus.PERMANENT_REDIRECT
    ).contains(this.status)

fun String.toUri() = URI(this)

fun HttpResponse<*>.getRedirectionUri(): URI? =
    if (isRedirected()) {
        header(HttpHeaders.LOCATION)?.toUri()
    } else null
