package com.kuvaszuptime.kuvasz.services.ui

import java.time.OffsetDateTime
import kotlin.time.toKotlinDuration

object IntervalTransformer {

    private const val HOURS_IN_A_DAY = 24
    private const val MINUTES_IN_AN_HOUR = 60
    private const val SECONDS_IN_A_MINUTE = 60

    fun transform(start: OffsetDateTime, end: OffsetDateTime): String {
        val duration = java.time.Duration.between(start, end).toKotlinDuration()
        val days = duration.inWholeDays
        val hours = duration.inWholeHours % HOURS_IN_A_DAY
        val minutes = duration.inWholeMinutes % MINUTES_IN_AN_HOUR
        val seconds = duration.inWholeSeconds % SECONDS_IN_A_MINUTE

        return listOfNotNull(
            if (days > 1) "$days days" else if (days == 1L) "$days day" else null,
            if (hours > 1) "$hours hours" else if (hours == 1L) "$hours hour" else null,
            if (minutes > 1) "$minutes minutes" else if (minutes == 1L) "$minutes minute" else null,
            if (seconds > 1) "$seconds seconds" else if (seconds == 1L) "$seconds second" else null,
        ).asSequence().take(2).joinToString(" ")
    }
}
