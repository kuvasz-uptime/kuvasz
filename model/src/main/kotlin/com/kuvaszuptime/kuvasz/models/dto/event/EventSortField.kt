package com.kuvaszuptime.kuvasz.models.dto.event

import io.micronaut.core.annotation.Introspected

@Introspected
enum class EventSortField {
    ID,
    STATUS,
    STARTED_AT,
    ENDED_AT,
    UPDATED_AT,
}
