package com.kuvaszuptime.kuvasz.models.checks

import io.micronaut.http.HttpStatus

object SupportedExpectedHttpStatusCodes {
    @Suppress("MagicNumber")
    val allCodes = HttpStatus.entries.filter { it.code < 500 }.map { HttpStatusCode(it.code, it.reason) }
}

data class HttpStatusCode(
    val code: Int,
    val reason: String,
)
