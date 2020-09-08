package com.kuvaszuptime.kuvasz.formatters

object TelegramTextFormatter : TextFormatter {
    override fun bold(input: String): String = "<b>$input</b>"

    override fun italic(input: String): String = "<i>$input</i>"
}
