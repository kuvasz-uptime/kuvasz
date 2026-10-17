package com.kuvaszuptime.kuvasz.models.dto.monitor.push

import io.micronaut.core.annotation.Introspected

@Introspected
enum class PushMonitorSortField {
    ID,
    NAME,
    ENABLED,
    UPTIME_STATUS,
    UPTIME_STATUS_STARTED_AT,
    LAST_UPTIME_CHECK,
    HEARTBEAT_INTERVAL,
    LAST_HEARTBEAT,
    CREATED_AT,
    UPDATED_AT,
}
