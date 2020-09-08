package com.kuvaszuptime.kuvasz.formatters

object TelegramTextFormatter : TextMessageFormatter() {
    override fun bold(input: String): String = "<b>$input</b>"

    override fun italic(input: String): String = "<i>$input</i>"
}
