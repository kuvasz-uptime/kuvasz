package com.kuvaszuptime.kuvasz.util

import io.micronaut.http.HttpResponse
import java.net.URI
import kotlin.jvm.optionals.getOrNull

fun String.toUri(): URI = URI(this)

inline fun <reified T : Any> HttpResponse<*>.getBodyAs(): T? = getBody(T::class.java).getOrNull()
