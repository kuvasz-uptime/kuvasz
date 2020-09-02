package com.kuvaszuptime.kuvasz.models

import io.micronaut.core.annotation.Introspected

@Suppress("ConstructorParameterNaming")
@Introspected
data class TelegramAPIMessage(
    val chat_id: String,
    val text: String,
    val disable_web_page_preview: Boolean = true,
    val parse_mode: String = "HTML"
)
