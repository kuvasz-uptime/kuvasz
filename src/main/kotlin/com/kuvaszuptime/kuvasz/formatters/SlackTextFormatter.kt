package com.kuvaszuptime.kuvasz.formatters

object SlackTextFormatter : TextMessageFormatter() {
    override fun bold(input: String): String = "*$input*"

    override fun italic(input: String): String = "_${input}_"
}
