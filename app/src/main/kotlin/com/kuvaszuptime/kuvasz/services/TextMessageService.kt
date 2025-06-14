package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import io.reactivex.rxjava3.core.Single

interface TextMessageService {
    fun sendMessage(integrationConfig: IntegrationConfig, content: String): Single<String>
}
