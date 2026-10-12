package com.kuvaszuptime.kuvasz.models.events.formatters

object DiscordTextFormatter : RichTextMessageFormatter() {
    override fun bold(input: String): String = "**$input**"

    override fun italic(input: String): String = "*$input*"

    // Discord doesn't interpret &, < and > as a markup (like Slack or Telegram), it has no entities to escape with
    override fun escape(input: String): String = input
}
