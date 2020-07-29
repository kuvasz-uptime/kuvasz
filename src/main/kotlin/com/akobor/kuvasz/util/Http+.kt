package com.akobor.kuvasz.util

import arrow.core.Option
import io.micronaut.http.HttpResponse
import java.net.URI

@Suppress("MagicNumber")
fun HttpResponse<*>.isSuccess() = this.status.code in 200..299

@Suppress("MagicNumber")
fun HttpResponse<*>.isRedirected() = listOf(301, 302, 303, 307).contains(this.status.code)

fun String.toUri() = URI(this)

fun HttpResponse<*>.getRedirectionUri(): Option<URI> =
    Option.fromNullable(
        if (isRedirected()) {
            header("Location")?.toUri()
        } else {
            null
        }
    )
