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
    const val GET_INCIDENT_DETAILS = "get-incident-details"
    const val CREATE_INCIDENT_NOTE = "create-incident-note"
    const val DELETE_INCIDENT_NOTE = "delete-incident-note"
    const val LIST_PUSH_MONITORS = "list-push-monitors"
    const val LIST_STATUS_PAGES = "list-status-pages"
    const val LIST_MAINTENANCE_WINDOWS = "list-maintenance-windows"
    const val GET_MAINTENANCE_WINDOW_DETAILS = "get-maintenance-window-details"
    const val CREATE_MAINTENANCE_WINDOW = "create-maintenance-window"
    const val TOGGLE_MAINTENANCE_WINDOW = "toggle-maintenance-window"
    const val DELETE_MAINTENANCE_WINDOW = "delete-maintenance-window"
    const val DELETE_HTTP_MONITOR = "delete-http-monitor"
    const val DELETE_ICMP_MONITOR = "delete-icmp-monitor"
    const val DELETE_PUSH_MONITOR = "delete-push-monitor"
    const val TOGGLE_HTTP_MONITOR = "toggle-http-monitor"
    const val TOGGLE_ICMP_MONITOR = "toggle-icmp-monitor"
    const val TOGGLE_PUSH_MONITOR = "toggle-push-monitor"
    const val CREATE_TCP_MONITOR = "create-tcp-monitor"
    const val GET_TCP_MONITOR_DETAILS = "get-tcp-monitor-details"
    const val GET_TCP_MONITOR_STATS = "get-tcp-monitor-stats"
    const val LIST_TCP_MONITORS = "list-tcp-monitors"
    const val DELETE_TCP_MONITOR = "delete-tcp-monitor"
    const val TOGGLE_TCP_MONITOR = "toggle-tcp-monitor"
    const val CREATE_DNS_MONITOR = "create-dns-monitor"
    const val GET_DNS_MONITOR_DETAILS = "get-dns-monitor-details"
    const val GET_DNS_MONITOR_STATS = "get-dns-monitor-stats"
    const val LIST_DNS_MONITORS = "list-dns-monitors"
    const val DELETE_DNS_MONITOR = "delete-dns-monitor"
    const val TOGGLE_DNS_MONITOR = "toggle-dns-monitor"
    const val CREATE_DOCKER_MONITOR = "create-docker-monitor"
    const val GET_DOCKER_MONITOR_DETAILS = "get-docker-monitor-details"
    const val GET_DOCKER_MONITOR_STATS = "get-docker-monitor-stats"
    const val LIST_DOCKER_MONITORS = "list-docker-monitors"
    const val DELETE_DOCKER_MONITOR = "delete-docker-monitor"
    const val TOGGLE_DOCKER_MONITOR = "toggle-docker-monitor"
    const val LIST_DOCKER_HOSTS = "list-docker-hosts"
    const val LIST_PROXIES = "list-proxies"
}

object ListToolArgs {
    const val PAGE = "The number of the page to return, starting from 1. Defaults to 1."
    const val SIZE = "The number of items on a page. Defaults to 25."
    const val SORTED_BY =
        "The field to sort by, e.g. 'NAME' or 'ID'. An unknown field is rejected with the list of the allowed ones."
    const val SORT_ORDER = "'ASC' or 'DESC'. Defaults to 'ASC' if 'sortedBy' is given."
    const val MONITOR_NAME =
        "Returns only the monitors whose name contains this text, ignoring the case of the letters."
    const val INCIDENT_MONITOR_NAME =
        "Returns only the incidents whose monitor's name contains this text, ignoring the case of the letters."
    const val STATUS_PAGE_TITLE =
        "Returns only the status pages whose title contains this text, ignoring the case of the letters."
    const val MAINTENANCE_WINDOW_NAME =
        "Returns only the maintenance windows whose name contains this text, ignoring the case of the letters."
    const val PAGINATION_NOTE = " The result is one page (25 items by default); it tells the total number of " +
        "items and pages, use 'page' to get the next ones."
}
