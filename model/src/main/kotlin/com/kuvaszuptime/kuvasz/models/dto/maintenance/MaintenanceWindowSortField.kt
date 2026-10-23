package com.kuvaszuptime.kuvasz.models.dto.maintenance

import io.micronaut.core.annotation.Introspected

@Introspected
enum class MaintenanceWindowSortField {
    ID,
    NAME,
    ENABLED,
    GLOBAL,
    SHOW_ON_STATUS_PAGES,
    START,
    CREATED_AT,
    UPDATED_AT,
}
