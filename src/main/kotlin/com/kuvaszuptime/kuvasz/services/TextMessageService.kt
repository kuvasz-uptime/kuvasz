package com.kuvaszuptime.kuvasz.services

import io.micronaut.http.HttpResponse
import io.reactivex.Flowable

interface TextMessageService {
    fun sendMessage(content: String): Flowable<HttpResponse<String>>
}
