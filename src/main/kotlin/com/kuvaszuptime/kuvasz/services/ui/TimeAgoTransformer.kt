package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.abs

@Suppress("MaxLineLength")
object TimeAgoTransformer {

    private const val SECONDS_IN_MINUTE = 60
    private const val SECONDS_IN_HOUR = SECONDS_IN_MINUTE * 60
    private const val SECONDS_IN_DAY = SECONDS_IN_HOUR * 24
    private const val SECONDS_IN_WEEK = SECONDS_IN_DAY * 7
    private const val SECONDS_IN_MONTH = SECONDS_IN_DAY * 30 // Approximation
    private const val SECONDS_IN_YEAR = SECONDS_IN_DAY * 365 // Approximation

    @Suppress("CyclomaticComplexMethod")
    fun transform(dateTime: OffsetDateTime, reference: OffsetDateTime = getCurrentTimestamp()): String {
        val seconds = Duration.between(dateTime, reference).seconds
        val isNegative = seconds < 0
        val absSeconds = abs(seconds)

        return when {
            absSeconds == 0L -> "just now"
            absSeconds < SECONDS_IN_MINUTE -> if (isNegative) "in $absSeconds seconds" else "$absSeconds seconds ago"
            absSeconds < SECONDS_IN_MINUTE * 2 -> if (isNegative) "in a minute" else "a minute ago"
            absSeconds < SECONDS_IN_HOUR -> if (isNegative) "in ${absSeconds / SECONDS_IN_MINUTE} minutes" else "${absSeconds / SECONDS_IN_MINUTE} minutes ago"
            absSeconds < SECONDS_IN_HOUR * 2 -> if (isNegative) "in an hour" else "an hour ago"
            absSeconds < SECONDS_IN_DAY -> if (isNegative) "in ${absSeconds / SECONDS_IN_HOUR} hours" else "${absSeconds / SECONDS_IN_HOUR} hours ago"
            absSeconds < SECONDS_IN_DAY * 2 -> if (isNegative) "in a day" else "a day ago"
            absSeconds < SECONDS_IN_WEEK -> if (isNegative) "in ${absSeconds / SECONDS_IN_DAY} days" else "${absSeconds / SECONDS_IN_DAY} days ago"
            absSeconds < SECONDS_IN_WEEK * 2 -> if (isNegative) "in a week" else "a week ago"
            absSeconds < SECONDS_IN_MONTH -> if (isNegative) "in ${absSeconds / SECONDS_IN_WEEK} weeks" else "${absSeconds / SECONDS_IN_WEEK} weeks ago"
            absSeconds < SECONDS_IN_MONTH * 2 -> if (isNegative) "in a month" else "a month ago"
            absSeconds < SECONDS_IN_YEAR -> if (isNegative) "in ${absSeconds / SECONDS_IN_MONTH} months" else "${absSeconds / SECONDS_IN_MONTH} months ago"
            absSeconds < SECONDS_IN_YEAR * 2 -> if (isNegative) "in a year" else "a year ago"
            else -> if (isNegative) "in ${absSeconds / SECONDS_IN_YEAR} years" else "${absSeconds / SECONDS_IN_YEAR} years ago"
        }
    }
}
