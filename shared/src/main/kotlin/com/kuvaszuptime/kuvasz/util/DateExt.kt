package com.kuvaszuptime.kuvasz.util

import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.i18n.Messages.aDayAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.aMinuteAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.aMonthAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.aWeekAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.aYearAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.anHourAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.daysAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.hoursAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.inADay
import com.kuvaszuptime.kuvasz.i18n.Messages.inAMinute
import com.kuvaszuptime.kuvasz.i18n.Messages.inAMonth
import com.kuvaszuptime.kuvasz.i18n.Messages.inAWeek
import com.kuvaszuptime.kuvasz.i18n.Messages.inAYear
import com.kuvaszuptime.kuvasz.i18n.Messages.inAnHour
import com.kuvaszuptime.kuvasz.i18n.Messages.inDays
import com.kuvaszuptime.kuvasz.i18n.Messages.inHours
import com.kuvaszuptime.kuvasz.i18n.Messages.inMinutes
import com.kuvaszuptime.kuvasz.i18n.Messages.inMonths
import com.kuvaszuptime.kuvasz.i18n.Messages.inSeconds
import com.kuvaszuptime.kuvasz.i18n.Messages.inWeeks
import com.kuvaszuptime.kuvasz.i18n.Messages.inYears
import com.kuvaszuptime.kuvasz.i18n.Messages.justNow
import com.kuvaszuptime.kuvasz.i18n.Messages.minutesAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.monthsAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.secondsAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.weeksAgo
import com.kuvaszuptime.kuvasz.i18n.Messages.yearsAgo
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Date
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toKotlinDuration
import java.time.Duration as JavaDuration

fun getCurrentTimestamp(): OffsetDateTime = OffsetDateTime.now(ZoneId.systemDefault())

fun Duration?.toDurationString(): String? = this?.toComponents { days, hours, minutes, seconds, _ ->
    Messages.durationParts(days, hours, minutes, seconds)
}

fun Int.toDurationOfSeconds(): java.time.Duration = java.time.Duration.ofSeconds(toLong())

fun Date.toOffsetDateTime(): OffsetDateTime = toInstant().toOffsetDateTime()

fun OffsetDateTime.diffToDuration(endDateTime: OffsetDateTime): Duration =
    (endDateTime.toEpochSecond() - this.toEpochSecond()).toDuration(DurationUnit.SECONDS)

fun Instant.toOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(this, ZoneId.systemDefault())

private const val HOURS_IN_A_DAY = 24
private const val MINUTES_IN_AN_HOUR = 60
private const val SECONDS_IN_A_MINUTE = 60
private const val SECONDS_IN_HOUR = SECONDS_IN_A_MINUTE * MINUTES_IN_AN_HOUR
private const val SECONDS_IN_DAY = SECONDS_IN_HOUR * HOURS_IN_A_DAY
private const val SECONDS_IN_WEEK = SECONDS_IN_DAY * 7
private const val SECONDS_IN_MONTH = SECONDS_IN_DAY * 30 // Approximation
private const val SECONDS_IN_YEAR = SECONDS_IN_DAY * 365 // Approximation

@Suppress("MaxLineLength", "CyclomaticComplexMethod", "MultiLineIfElse", "ArgumentListWrapping")
fun OffsetDateTime.timeAgo(reference: OffsetDateTime = getCurrentTimestamp()): String {
    val seconds = JavaDuration.between(this, reference).seconds
    val isNegative = seconds < 0
    val absSeconds = abs(seconds)

    return when {
        absSeconds == 0L -> justNow()
        absSeconds < SECONDS_IN_A_MINUTE -> if (isNegative) inSeconds(absSeconds) else secondsAgo(absSeconds)
        absSeconds < SECONDS_IN_A_MINUTE * 2 -> if (isNegative) inAMinute() else aMinuteAgo()
        absSeconds < SECONDS_IN_HOUR ->
            if (isNegative) inMinutes(absSeconds / SECONDS_IN_A_MINUTE) else minutesAgo(absSeconds / SECONDS_IN_A_MINUTE)

        absSeconds < SECONDS_IN_HOUR * 2 -> if (isNegative) inAnHour() else anHourAgo()
        absSeconds < SECONDS_IN_DAY -> if (isNegative) inHours(absSeconds / SECONDS_IN_HOUR) else hoursAgo(
            absSeconds / SECONDS_IN_HOUR
        )

        absSeconds < SECONDS_IN_DAY * 2 -> if (isNegative) inADay() else aDayAgo()
        absSeconds < SECONDS_IN_WEEK ->
            if (isNegative) inDays(absSeconds / SECONDS_IN_DAY) else daysAgo(absSeconds / SECONDS_IN_DAY)

        absSeconds < SECONDS_IN_WEEK * 2 -> if (isNegative) inAWeek() else aWeekAgo()
        absSeconds < SECONDS_IN_MONTH -> if (isNegative) inWeeks(absSeconds / SECONDS_IN_WEEK) else weeksAgo(
            absSeconds / SECONDS_IN_WEEK
        )

        absSeconds < SECONDS_IN_MONTH * 2 -> if (isNegative) inAMonth() else aMonthAgo()
        absSeconds < SECONDS_IN_YEAR -> if (isNegative) inMonths(absSeconds / SECONDS_IN_MONTH) else monthsAgo(
            absSeconds / SECONDS_IN_MONTH
        )

        absSeconds < SECONDS_IN_YEAR * 2 -> if (isNegative) inAYear() else aYearAgo()
        else ->
            if (isNegative) inYears(absSeconds / SECONDS_IN_YEAR) else yearsAgo(absSeconds / SECONDS_IN_YEAR)
    }
}

/**
 * Calculates the duration between two [OffsetDateTime] instances and formats it as a human-readable string. Alwqys takes
 * only the first two non-zero intervals (days, hours, minutes, seconds) to keep the output concise.
 */
@Suppress("MaxLineLength", "ArgumentListWrapping")
fun OffsetDateTime.durationBetween(
    end: OffsetDateTime = getCurrentTimestamp()
): String {
    val duration = java.time.Duration.between(this, end).toKotlinDuration()
    val days = duration.inWholeDays
    val hours = duration.inWholeHours % HOURS_IN_A_DAY
    val minutes = duration.inWholeMinutes % MINUTES_IN_AN_HOUR
    val seconds = duration.inWholeSeconds % SECONDS_IN_A_MINUTE

    return listOfNotNull(
        if (days > 1) Messages.daysInterval(days) else if (days == 1L) Messages.dayInterval(days) else null,
        if (hours > 1) Messages.hoursInterval(hours) else if (hours == 1L) Messages.hourInterval(hours) else null,
        if (minutes > 1) Messages.minutesInterval(minutes) else if (minutes == 1L) Messages.minuteInterval(minutes) else null,
        if (seconds > 1) Messages.secondsInterval(seconds) else if (seconds == 1L) Messages.secondInterval(seconds) else null,
    ).asSequence().take(2).joinToString(" ")
}
