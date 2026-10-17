package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

import io.micronaut.core.annotation.Introspected

@Introspected
enum class DockerMonitorSortField {
    ID,
    NAME,
    ENABLED,
    UPTIME_STATUS,
    UPTIME_STATUS_STARTED_AT,
    LAST_UPTIME_CHECK,
    UPTIME_CHECK_INTERVAL,
    DOCKER_HOST,
    CONTAINER,
    CREATED_AT,
    UPDATED_AT,
}
