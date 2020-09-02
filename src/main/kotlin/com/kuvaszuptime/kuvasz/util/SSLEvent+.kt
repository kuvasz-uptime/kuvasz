@file:Suppress("CommentSpacing")
package com.kuvaszuptime.kuvasz.util

import arrow.core.Option
import com.kuvaszuptime.kuvasz.enums.SslStatus
import com.kuvaszuptime.kuvasz.models.Emoji
import com.kuvaszuptime.kuvasz.models.SSLInvalidEvent
import com.kuvaszuptime.kuvasz.models.SSLMonitorEvent
import com.kuvaszuptime.kuvasz.models.SSLValidEvent
import com.kuvaszuptime.kuvasz.models.SSLWillExpireEvent
import com.kuvaszuptime.kuvasz.tables.pojos.SslEventPojo
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun SSLMonitorEvent.toSSLStatus(): SslStatus =
    when (this) {
        is SSLValidEvent -> SslStatus.VALID
        is SSLWillExpireEvent -> SslStatus.WILL_EXPIRE
        is SSLInvalidEvent -> SslStatus.INVALID
    }

fun SSLMonitorEvent.toEmoji(): String =
    when (this) {
        is SSLValidEvent -> Emoji.CHECK_OK
        is SSLInvalidEvent -> Emoji.ALERT
        is SSLWillExpireEvent -> Emoji.WARNING
    }

fun SSLMonitorEvent.sslStatusEquals(previousEvent: SslEventPojo) =
    toSSLStatus() == previousEvent.status

fun SSLMonitorEvent.sslStatusNotEquals(previousEvent: SslEventPojo) =
    !sslStatusEquals(previousEvent)

fun SSLMonitorEvent.getEndedEventDuration(): Option<Duration> =
    previousEvent.flatMap { previousEvent ->
        Option.fromNullable(
            if (sslStatusNotEquals(previousEvent)) {
                val diff = dispatchedAt.toEpochSecond() - previousEvent.startedAt.toEpochSecond()
                diff.toDuration(DurationUnit.SECONDS)
            } else null
        )
    }

fun SSLMonitorEvent.runWhenStateChanges(toRun: (SSLMonitorEvent) -> Unit) {
    return previousEvent.fold(
        { toRun(this) },
        { previousEvent ->
            if (sslStatusNotEquals(previousEvent)) {
                toRun(this)
            }
        }
    )
}

//TODO
//fun SSLValidEvent.toPlainMessage(): String =
//    toStructuredMessage().let { details ->
//        listOfNotNull(
//            details.summary,
//            details.latency,
//            details.previousDownTime.orNull()
//        ).joinToString(". ")
//    }
//
//fun SSLValidEvent.toStructuredMessage() =
//    StructuredSSLValidMessage(
//        summary = "Your monitor \"${monitor.name}\" (${monitor.url}) is UP (${status.code})",
//        latency = "Latency: ${latency}ms",
//        previousDownTime = getEndedEventDuration().toDurationString().map { "Was down for $it" }
//    )

//fun MonitorDownEvent.toPlainMessage(): String =
//    toStructuredMessage().let { details ->
//        listOfNotNull(
//            details.summary,
//            details.error,
//            details.previousUpTime.orNull()
//        ).joinToString(". ")
//    }
//
//fun MonitorDownEvent.toStructuredMessage() =
//    StructuredDownMessage(
//        summary = "Your monitor \"${monitor.name}\" (${monitor.url}) is DOWN" +
//                status.toOption().map { " (" + it.code + ")" }.getOrElse { "" },
//        error = "Reason: ${error.message}",
//        previousUpTime = getEndedEventDuration().toDurationString().map { "Was up for $it" }
//    )
