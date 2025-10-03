package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpRedirectEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
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

fun MonitorEvent<*>.getEmoji(): String =
    when (this) {
        is HttpMonitorUpEvent, is PushMonitorUpEvent -> Emoji.CHECK_OK
        is HttpMonitorDownEvent, is PushMonitorDownEvent -> Emoji.ALERT
        is HttpRedirectEvent -> Emoji.INFO
        is SSLValidEvent -> Emoji.LOCK
        is SSLInvalidEvent -> Emoji.ALERT
        is SSLWillExpireEvent -> Emoji.WARNING
    }
