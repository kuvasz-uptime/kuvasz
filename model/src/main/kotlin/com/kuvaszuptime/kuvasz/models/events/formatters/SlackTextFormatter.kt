package com.kuvaszuptime.kuvasz.models.events.formatters

object SlackTextFormatter : RichTextMessageFormatter() {
    override fun bold(input: String): String = "*$input*"

    override fun italic(input: String): String = "_${input}_"

    // Slack interprets only these as control characters, e.g. in <!channel> or <https://example.com|link>
    // See: https://docs.slack.dev/messaging/formatting-message-text#escaping
    override fun escape(input: String): String =
        input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun strikethrough(input: String): String = "~$input~"

    fun strikethroughHeadline(message: String): String =
        (listOf(strikethrough(message.lines().first())) + message.lines().drop(1)).joinToString("\n")
}
