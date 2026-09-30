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

    val PERIOD_SELECTOR_OPTIONS: List<Duration> = listOf(
        Duration.ofHours(ONE_HOUR),
        Duration.ofHours(SIX_HOURS),
        Duration.ofHours(TWELVE_HOURS),
        Duration.ofDays(ONE_DAY),
        Duration.ofDays(SEVEN_DAYS),
        Duration.ofDays(THIRTY_DAYS),
    )
}
