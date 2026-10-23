package com.kuvaszuptime.kuvasz.models.dto.incident

import io.micronaut.core.annotation.Introspected

@Introspected
enum class IncidentSortField {
    ID,
    MONITOR_NAME,
    INCIDENT_TYPE,
    STATUS,
    STARTED_AT,
    ENDED_AT,
    UPDATED_AT,
}
