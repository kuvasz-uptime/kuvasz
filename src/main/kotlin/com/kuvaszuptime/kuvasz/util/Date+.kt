package com.kuvaszuptime.kuvasz.util

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun getCurrentTimestamp(): OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))

fun Duration?.toDurationString(): String? = this?.let { duration ->
    duration.toComponents { days, hours, minutes, seconds, _ ->
        "$days day(s), $hours hour(s), $minutes minute(s), $seconds second(s)"
    }
}

fun Int.toDurationOfSeconds(): java.time.Duration = java.time.Duration.ofSeconds(toLong())

fun Date.toOffsetDateTime(): OffsetDateTime = toInstant().atOffset(ZoneOffset.UTC)

fun OffsetDateTime.diffToDuration(endDateTime: OffsetDateTime): Duration =
    (endDateTime.toEpochSecond() - this.toEpochSecond()).toDuration(DurationUnit.SECONDS)
