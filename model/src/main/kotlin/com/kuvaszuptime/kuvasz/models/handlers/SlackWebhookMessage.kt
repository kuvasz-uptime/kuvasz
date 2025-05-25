package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import java.net.URI

@Introspected
data class SlackWebhookMessage(
    val username: String = "KuvaszBot",
    @field:JsonProperty("icon_url")
    val iconUrl: URI = URI(ICON_URL),
    val text: String
) {
    companion object {
        const val ICON_URL = "https://raw.githubusercontent.com/kuvasz-uptime/kuvasz/main/docs/kuvasz_avatar.png"
    }
}
