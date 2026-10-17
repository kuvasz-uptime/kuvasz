package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

import io.micronaut.core.annotation.Introspected

@Introspected
enum class TcpMonitorSortField {
    ID,
    NAME,
    ENABLED,
    UPTIME_STATUS,
    UPTIME_STATUS_STARTED_AT,
    LAST_UPTIME_CHECK,
    UPTIME_CHECK_INTERVAL,
    HOST,
    PORT,
    CREATED_AT,
    UPDATED_AT,
}
