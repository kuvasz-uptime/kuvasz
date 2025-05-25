package com.kuvaszuptime.kuvasz.models.handlers

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
data class TelegramAPIMessage(
    @field:JsonProperty("chat_id")
    val chatId: String,
    val text: String,
    @field:JsonProperty("disable_web_page_preview")
    val disableWebPagePreview: Boolean = true,
    @field:JsonProperty("parse_mode")
    val parseMode: String = "HTML"
)
