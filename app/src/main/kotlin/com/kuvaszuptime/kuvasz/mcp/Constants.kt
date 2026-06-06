package com.kuvaszuptime.kuvasz.mcp

import java.time.Duration

val DEFAULT_STATS_PERIOD: Duration = Duration.ofDays(1)

object ToolNames {
    const val CREATE_HTTP_MONITOR = "create-http-monitor"
    const val CREATE_ICMP_MONITOR = "create-icmp-monitor"
    const val GET_HTTP_MONITOR_DETAILS = "get-http-monitor-details"
    const val GET_HTTP_MONITOR_STATS = "get-http-monitor-stats"
    const val GET_ICMP_MONITOR_DETAILS = "get-icmp-monitor-details"
    const val GET_ICMP_MONITOR_STATS = "get-icmp-monitor-stats"
    const val GET_PUSH_MONITOR_DETAILS = "get-push-monitor-details"
    const val GET_PUSH_MONITOR_STATS = "get-push-monitor-stats"
    const val LIST_HTTP_MONITORS = "list-http-monitors"
    const val LIST_ICMP_MONITORS = "list-icmp-monitors"
    const val LIST_INCIDENTS = "list-incidents"
    const val LIST_PUSH_MONITORS = "list-push-monitors"
    const val UPDATE_HTTP_MONITOR = "update-http-monitor"
    const val UPDATE_ICMP_MONITOR = "update-icmp-monitor"
    const val UPDATE_PUSH_MONITOR = "update-push-monitor"
}
