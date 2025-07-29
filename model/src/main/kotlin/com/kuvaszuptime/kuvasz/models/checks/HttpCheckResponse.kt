package com.kuvaszuptime.kuvasz.models.checks

import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.HttpResponse

data class HttpCheckResponse(
    val httpResponse: RawHttpResponse,
    val latency: Int,
)

typealias RawHttpResponse = HttpResponse<ByteBuffer<Any>>
