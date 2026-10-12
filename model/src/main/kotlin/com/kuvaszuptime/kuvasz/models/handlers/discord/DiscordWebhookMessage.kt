package com.kuvaszuptime.kuvasz.models.handlers.discord

import io.micronaut.core.annotation.Introspected

@Introspected
data class DiscordWebhookMessage(
    val username: String = "KuvaszBot",
    val content: String
)
