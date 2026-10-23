package com.kuvaszuptime.kuvasz.models.dto.monitor.http

import io.micronaut.core.annotation.Introspected

@Introspected
enum class HttpMonitorSortField {
    ID,
    NAME,
    ENABLED,
    UPTIME_STATUS,
    UPTIME_STATUS_STARTED_AT,
    LAST_UPTIME_CHECK,
    UPTIME_CHECK_INTERVAL,
    URL,
    SSL_STATUS,
    SSL_VALID_UNTIL,
    CREATED_AT,
    UPDATED_AT,
}
