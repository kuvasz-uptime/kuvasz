package com.kuvaszuptime.kuvasz.services.monitor

import java.time.Duration

private const val FIFTEEN = 15L
private const val SEVEN = 7L
private const val THIRTY = 30L

private val FIFTEEN_MINUTES: Duration = Duration.ofMinutes(FIFTEEN)
private val ONE_HOUR: Duration = Duration.ofHours(1)
private val SEVEN_DAYS: Duration = Duration.ofDays(SEVEN)
private val THIRTY_DAYS: Duration = Duration.ofDays(THIRTY)

/**
 * The length of the buckets that the checks of a chart's period are grouped into, or null to show every check. A chart
 * is only a few hundred pixels wide, so the tens of thousands of checks of a frequently checked monitor in a long
 * period would only make the response huge: 7 days in quarters of an hour are 672 points, 30 days in hours are 720.
 */
fun chartResolutionOf(period: Duration): Duration? = when {
    period >= THIRTY_DAYS -> ONE_HOUR
    period >= SEVEN_DAYS -> FIFTEEN_MINUTES
    else -> null
}
