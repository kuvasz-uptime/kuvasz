package com.kuvaszuptime.kuvasz.models.events.formatters

object MsTeamsTextFormatter : RichTextMessageFormatter() {
    override fun bold(input: String): String = "**$input**"

    override fun italic(input: String): String = "_${input}_"
}
