package com.kuvaszuptime.kuvasz.models.events.formatters

object DiscordTextFormatter : RichTextMessageFormatter() {
    override fun bold(input: String): String = "**$input**"

    override fun italic(input: String): String = "*$input*"
}
