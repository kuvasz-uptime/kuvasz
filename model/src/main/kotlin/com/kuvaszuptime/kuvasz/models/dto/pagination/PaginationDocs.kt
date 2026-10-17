package com.kuvaszuptime.kuvasz.models.dto.pagination

object PaginationDocs {
    const val PAGE = "The number of the page to return, starting from 1. A page after the last one returns no items."
    const val SIZE = "The number of items on a page"
    const val SORT_ORDER = "The order of the sorting, ASC if it's not given"
    const val MONITOR_NAME_FILTER =
        "Returns only the monitors whose name contains this text, without regard to the case of the letters"
    const val MONITOR_SORTED_BY =
        "The field to sort the monitors by, NAME if it's not given. Monitors with the same value are sorted by their " +
            "ID. Monitors without a value (e.g. no check has completed yet) come last, whatever the order is."
}
