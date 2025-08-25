package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent

object Emoji {
    const val ALERT = "🚨"
    const val CHECK_OK = "✅"
    const val WARNING = "⚠️"
    const val INFO = "ℹ️"
    const val LOCK = "🔒️"
}

fun HttpMonitorEvent.getEmoji(): String =
    when (this) {
        is HttpMonitorUpEvent -> Emoji.CHECK_OK
        is HttpMonitorDownEvent -> Emoji.ALERT
        is HttpRedirectEvent -> Emoji.INFO
        is SSLValidEvent -> Emoji.LOCK
        is SSLInvalidEvent -> Emoji.ALERT
        is SSLWillExpireEvent -> Emoji.WARNING
    }
