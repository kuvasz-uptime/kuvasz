package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.Sorting

object IncidentQueryDefaults {
    const val PERIOD_DAYS = 7L
    const val INCLUDE_RESOLVED = true
    val SORTING = Sorting(IncidentSortField.STARTED_AT, SortOrder.DESC)
}
