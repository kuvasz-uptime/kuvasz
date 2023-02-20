package com.kuvaszuptime.kuvasz.services

import io.reactivex.rxjava3.core.Single

interface TextMessageService {
    fun sendMessage(content: String): Single<String>
}
