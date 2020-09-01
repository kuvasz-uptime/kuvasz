package com.kuvaszuptime.kuvasz.util

import arrow.core.Option
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import kotlin.time.Duration

fun getCurrentTimestamp(): OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))

fun Option<Duration>.toDurationString(): Option<String> = map { duration ->
    duration.toComponents { days, hours, minutes, seconds, _ ->
        "$days day(s), $hours hour(s), $minutes minute(s), $seconds second(s)"
    }
}

fun Int.toDurationOfSeconds(): java.time.Duration = java.time.Duration.ofSeconds(toLong())

fun Date.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)
