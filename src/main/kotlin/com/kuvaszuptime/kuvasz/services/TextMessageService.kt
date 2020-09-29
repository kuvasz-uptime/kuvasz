package com.kuvaszuptime.kuvasz.services

import io.reactivex.Single

interface TextMessageService {
    fun sendMessage(content: String): Single<String>
}
