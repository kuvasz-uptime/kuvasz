package com.kuvaszuptime.kuvasz.models.events.formatters

object DiscordTextFormatter : RichTextMessageFormatter() {
    override fun bold(input: String): String = "**$input**"

    override fun italic(input: String): String = "*$input*"

    // Not needed yet, because no free text written by the users (e.g. incident notes) is sent to Discord
    override fun escape(input: String): String = input
}
