package com.kuvaszuptime.kuvasz.models.events.formatters

object TelegramTextFormatter : RichTextMessageFormatter() {
    override fun bold(input: String): String = "<b>$input</b>"

    override fun italic(input: String): String = "<i>$input</i>"

    // See: https://core.telegram.org/bots/api#html-style
    override fun escape(input: String): String =
        input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
