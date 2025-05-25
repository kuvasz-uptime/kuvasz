package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.MonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.RedirectEvent
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

fun MonitorEvent.getEmoji(): String =
    when (this) {
        is MonitorUpEvent -> Emoji.CHECK_OK
        is MonitorDownEvent -> Emoji.ALERT
        is RedirectEvent -> Emoji.INFO
        is SSLValidEvent -> Emoji.LOCK
        is SSLInvalidEvent -> Emoji.ALERT
        is SSLWillExpireEvent -> Emoji.WARNING
    }
