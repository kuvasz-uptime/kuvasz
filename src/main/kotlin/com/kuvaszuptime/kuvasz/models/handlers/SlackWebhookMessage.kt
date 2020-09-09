package com.kuvaszuptime.kuvasz.models.handlers

import io.micronaut.core.annotation.Introspected
import java.net.URI

@Suppress("ConstructorParameterNaming")
@Introspected
data class SlackWebhookMessage(
    val username: String = "KuvaszBot",
    val icon_url: URI = URI(ICON_URL),
    val text: String
) {
    companion object {
        const val ICON_URL = "https://raw.githubusercontent.com/kuvasz-uptime/kuvasz/main/docs/kuvasz_avatar.png"
    }
}
