package com.kuvaszuptime.kuvasz.models.checks

import io.micronaut.http.HttpHeaders

object KnownHttpHeaders {
    val headerNames: Set<String> = HttpHeaders.STANDARD_HEADERS.toSet()
}
