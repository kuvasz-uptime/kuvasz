package com.kuvaszuptime.kuvasz.mcp

import java.time.Duration

val DEFAULT_STATS_PERIOD: Duration = Duration.ofDays(1)

object ToolNames {
    const val CREATE_HTTP_MONITOR = "create-http-monitor"
    const val LIST_INTEGRATIONS = "list-integrations"
    const val CREATE_ICMP_MONITOR = "create-icmp-monitor"
    const val CREATE_PUSH_MONITOR = "create-push-monitor"
    const val GET_APP_SETTINGS = "get-app-settings"
    const val GET_HTTP_MONITOR_DETAILS = "get-http-monitor-details"
    const val GET_HTTP_MONITOR_STATS = "get-http-monitor-stats"
    const val GET_ICMP_MONITOR_DETAILS = "get-icmp-monitor-details"
    const val GET_ICMP_MONITOR_STATS = "get-icmp-monitor-stats"
    const val GET_PUSH_MONITOR_DETAILS = "get-push-monitor-details"
    const val GET_PUSH_MONITOR_STATS = "get-push-monitor-stats"
    const val GET_STATUS_PAGE_DETAILS = "get-status-page-details"
    const val LIST_HTTP_MONITORS = "list-http-monitors"
    const val LIST_ICMP_MONITORS = "list-icmp-monitors"
    const val LIST_INCIDENTS = "list-incidents"
    const val LIST_PUSH_MONITORS = "list-push-monitors"
    const val LIST_STATUS_PAGES = "list-status-pages"
    const val DELETE_HTTP_MONITOR = "delete-http-monitor"
    const val DELETE_ICMP_MONITOR = "delete-icmp-monitor"
    const val DELETE_PUSH_MONITOR = "delete-push-monitor"
    const val TOGGLE_HTTP_MONITOR = "toggle-http-monitor"
    const val TOGGLE_ICMP_MONITOR = "toggle-icmp-monitor"
    const val TOGGLE_PUSH_MONITOR = "toggle-push-monitor"
}
