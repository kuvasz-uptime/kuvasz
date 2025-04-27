package com.kuvaszuptime.kuvasz.models

import com.kuvaszuptime.kuvasz.enums.HttpMethod

fun HttpMethod.toMicronautHttpMethod(): io.micronaut.http.HttpMethod {
    return when (this) {
        HttpMethod.GET -> io.micronaut.http.HttpMethod.GET
        HttpMethod.HEAD -> io.micronaut.http.HttpMethod.HEAD
    }
}
