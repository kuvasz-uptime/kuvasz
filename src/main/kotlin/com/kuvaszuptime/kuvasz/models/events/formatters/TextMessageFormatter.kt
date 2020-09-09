package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent

interface TextMessageFormatter {

    fun toFormattedMessage(event: UptimeMonitorEvent): String

    fun toFormattedMessage(event: SSLMonitorEvent): String
}
