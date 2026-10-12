package com.kuvaszuptime.kuvasz.models.events.formatters

object SlackTextFormatter : RichTextMessageFormatter() {
    override fun bold(input: String): String = "*$input*"

    override fun italic(input: String): String = "_${input}_"

    fun strikethrough(input: String): String = "~$input~"

    fun strikethroughHeadline(message: String): String =
        (listOf(strikethrough(message.lines().first())) + message.lines().drop(1)).joinToString("\n")
}
