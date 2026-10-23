package com.kuvaszuptime.kuvasz.services.monitor

import java.time.Duration

private const val MAX_CHART_POINTS = 1000L
private const val FIVE = 5L
private const val TEN = 10L
private const val FIFTEEN = 15L
private const val THIRTY = 30L
private const val THREE = 3L
private const val SIX = 6L
private const val TWELVE = 12L
private const val SEVEN = 7L
private const val SECONDS_IN_MINUTE = 60L
private const val SECONDS_IN_HOUR = 3_600L
private const val SECONDS_IN_DAY = 86_400L

private val SECOND_OR_MINUTE_COUNTS = listOf(1L, 2L, FIVE, TEN, FIFTEEN, THIRTY)

// Rounded up to one of these, so the buckets fall on whole minutes, hours, etc. and a point of the chart stays where it
// is as the newer checks arrive
private val NICE_BUCKET_WIDTHS_IN_SECONDS =
    SECOND_OR_MINUTE_COUNTS +
        SECOND_OR_MINUTE_COUNTS.map { it * SECONDS_IN_MINUTE } +
        listOf(1L, 2L, THREE, SIX, TWELVE).map { it * SECONDS_IN_HOUR } +
        listOf(1L, 2L, SEVEN).map { it * SECONDS_IN_DAY }

/**
 * The length of the buckets that the checks of a chart's [span] are grouped into, so that the chart has at most about
 * [MAX_CHART_POINTS] points whatever the number of the checks is. It only depends on the span, never on the monitor, so
 * the same span always has the same grid.
 */
fun chartBucketWidthOf(span: Duration): Duration {
    val minimumSeconds = (span.seconds + MAX_CHART_POINTS - 1) / MAX_CHART_POINTS
    val widthSeconds = NICE_BUCKET_WIDTHS_IN_SECONDS.firstOrNull { it >= minimumSeconds }
        ?: ((minimumSeconds + SECONDS_IN_DAY - 1) / SECONDS_IN_DAY * SECONDS_IN_DAY)

    return Duration.ofSeconds(widthSeconds)
}
