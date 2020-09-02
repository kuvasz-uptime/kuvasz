package com.kuvaszuptime.kuvasz.models

import io.micronaut.core.annotation.Introspected

@Suppress("ConstructorParameterNaming")
@Introspected
data class TelegramWebhookMessage(
    val chat_id: String,
    val text: String
)
