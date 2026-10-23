package com.kuvaszuptime.kuvasz.models.dto.statuspage

import io.micronaut.core.annotation.Introspected

@Introspected
enum class StatusPageSortField {
    ID,
    TITLE,
    SLUG,
    PUBLIC,
    CREATED_AT,
    UPDATED_AT,
}
