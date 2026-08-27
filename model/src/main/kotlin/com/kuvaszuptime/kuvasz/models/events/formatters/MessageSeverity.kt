package com.kuvaszuptime.kuvasz.models.events.formatters

import com.kuvaszuptime.kuvasz.models.events.DnsMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.DnsMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.IcmpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.events.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.events.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.TcpMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent

enum class MessageSeverity {
    CRITICAL,
    WARNING,
    OK,
    INFO,
}

fun UptimeMonitorEvent.getSeverity(): MessageSeverity =
    when (this) {
        is HttpMonitorUpEvent, is PushMonitorUpEvent, is IcmpMonitorUpEvent, is TcpMonitorUpEvent,
        is DnsMonitorUpEvent ->
            MessageSeverity.OK
        is HttpMonitorDownEvent, is PushMonitorDownEvent, is IcmpMonitorDownEvent, is TcpMonitorDownEvent,
        is DnsMonitorDownEvent ->
            MessageSeverity.CRITICAL
    }

fun SSLMonitorEvent.getSeverity(): MessageSeverity =
    when (this) {
        is SSLValidEvent -> MessageSeverity.OK
        is SSLInvalidEvent -> MessageSeverity.CRITICAL
        is SSLWillExpireEvent -> MessageSeverity.WARNING
    }

fun MaintenanceWindowEvent.getSeverity(): MessageSeverity =
    when (this) {
        is MaintenanceWindowStartEvent -> MessageSeverity.INFO
        is MaintenanceWindowEndEvent -> MessageSeverity.OK
    }
