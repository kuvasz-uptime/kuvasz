package com.kuvaszuptime.kuvasz.util

import arrow.core.Option
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.time.Duration

fun getCurrentTimestamp(): OffsetDateTime = OffsetDateTime.now(ZoneId.of("UTC"))

fun Option<Duration>.toDurationString(): Option<String> = map { duration ->
    duration.toComponents { days, hours, minutes, seconds, _ ->
        "$days day(s), $hours hour(s), $minutes minute(s), $seconds second(s)"
    }
}
