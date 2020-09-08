package com.kuvaszuptime.kuvasz.formatters

object SlackTextFormatter : TextFormatter {
    override fun bold(input: String): String = "*$input*"

    override fun italic(input: String): String = "_${input}_"
}
