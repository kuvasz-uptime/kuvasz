package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

import io.micronaut.core.annotation.Introspected

@Introspected
enum class IcmpMonitorSortField {
    ID,
    NAME,
    ENABLED,
    UPTIME_STATUS,
    UPTIME_STATUS_STARTED_AT,
    LAST_UPTIME_CHECK,
    UPTIME_CHECK_INTERVAL,
    HOST,
    CREATED_AT,
    UPDATED_AT,
}
