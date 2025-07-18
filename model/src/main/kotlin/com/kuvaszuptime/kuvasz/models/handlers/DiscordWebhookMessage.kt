package com.kuvaszuptime.kuvasz.models.handlers

import io.micronaut.core.annotation.Introspected

@Introspected
data class DiscordWebhookMessage(
    val username: String = "KuvaszBot",
    val content: String
)
