package com.kuvaszuptime.kuvasz.util

import java.time.Duration

object UIDefaults {
    const val DASHBOARD_MONITORING_STATS_PERIOD_DAYS = 7L
    const val HTTP_MONITOR_UPTIME_STATS_PERIOD_DAYS = 7L
    const val PUSH_MONITOR_UPTIME_STATS_PERIOD_DAYS = 7L
    const val ICMP_MONITOR_UPTIME_STATS_PERIOD_DAYS = 7L
    const val TCP_MONITOR_UPTIME_STATS_PERIOD_DAYS = 7L
    const val DNS_MONITOR_UPTIME_STATS_PERIOD_DAYS = 7L
    const val DOCKER_MONITOR_UPTIME_STATS_PERIOD_DAYS = 7L
    const val MONITOR_METRICS_PERIOD_DAYS = 1L
    const val INCIDENTS_PERIOD_DAYS = 7L
    const val STATUS_PAGE_PATH = "/status"

    private const val ONE_HOUR = 1L
    private const val SIX_HOURS = 6L
    private const val TWELVE_HOURS = 12L
    private const val ONE_DAY = 1L
    private const val SEVEN_DAYS = 7L
    private const val THIRTY_DAYS = 30L

    val LAST_HOUR: Duration = Duration.ofHours(ONE_HOUR)
    val LAST_6_HOURS: Duration = Duration.ofHours(SIX_HOURS)
    val LAST_12_HOURS: Duration = Duration.ofHours(TWELVE_HOURS)
    val LAST_24_HOURS: Duration = Duration.ofDays(ONE_DAY)
    val LAST_7_DAYS: Duration = Duration.ofDays(SEVEN_DAYS)
    val LAST_30_DAYS: Duration = Duration.ofDays(THIRTY_DAYS)

    val PERIOD_SELECTOR_OPTIONS: List<Duration> =
        listOf(LAST_HOUR, LAST_6_HOURS, LAST_12_HOURS, LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS)
}
