package com.kuvaszuptime.kuvasz.models.dto.pagination

object PaginationDocs {
    const val PAGE = "The number of the page to return, starting from 1. A page after the last one returns no items."
    const val SIZE = "The number of items on a page"
    const val SORT_ORDER = "The order of the sorting, ASC if it's not given"
    const val MONITOR_NAME_FILTER =
        "Returns only the monitors whose name contains this text, without regard to the case of the letters"
    const val MONITOR_SORTED_BY =
        "The field to sort the monitors by, NAME if it's not given. Monitors with the same value are sorted by their " +
            "ID. A monitor that has no value for the field yet (e.g. no UPTIME_STATUS before its first check) comes " +
            "last, whatever the order is."
    const val EVENT_SORTED_BY =
        "The field to sort the events by. Without sortedBy and sortOrder, the newest events come first " +
            "(STARTED_AT, DESC). Events with the same value are sorted by their ID. When sorted by ENDED_AT, the " +
            "ongoing events (which haven't ended yet) come last, whatever the order is."
    const val INCIDENT_SORTED_BY =
        "The field to sort the incidents by. Without sortedBy and sortOrder, the latest started incidents come " +
            "first (STARTED_AT, DESC). ID is the ID of the event of the incident. Incidents with the same value are " +
            "sorted by their type and the ID of their event. When sorted by ENDED_AT, the ongoing incidents (which " +
            "haven't ended yet) come last, whatever the order is."
    const val INCIDENT_NAME_FILTER =
        "Returns only the incidents whose monitor's name contains this text, without regard to the case of the letters"
    const val STATUS_PAGE_SORTED_BY =
        "The field to sort the status pages by, TITLE if it's not given. Status pages with the same value are sorted " +
            "by their ID."
    const val STATUS_PAGE_NAME_FILTER =
        "Returns only the status pages whose title contains this text, without regard to the case of the letters"
    const val MAINTENANCE_WINDOW_SORTED_BY =
        "The field to sort the maintenance windows by, NAME if it's not given. Maintenance windows with the same " +
            "value are sorted by their ID. When sorted by START, the windows without a fixed start (the manual and " +
            "the recurring ones) come last, whatever the order is."
    const val MAINTENANCE_WINDOW_NAME_FILTER =
        "Returns only the maintenance windows whose name contains this text, without regard to the case of the letters"
}
