package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.HttpUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent

interface TextMessageFormatter {

    fun toFormattedMessage(event: HttpUptimeMonitorEvent): String

    fun toFormattedMessage(event: SSLMonitorEvent): String
}
