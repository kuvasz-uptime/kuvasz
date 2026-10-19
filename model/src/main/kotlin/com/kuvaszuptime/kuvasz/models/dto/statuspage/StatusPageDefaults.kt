package com.kuvaszuptime.kuvasz.models.dto.statuspage

object StatusPageDefaults {
    const val DEFAULT_PAGE_PUBLIC = false
    const val CUSTOM_PAGE_PUBLIC = false
    const val TITLE = "System status"
    const val DISPLAY_CATEGORIES = true
    const val HISTORY_DAYS = 30

    // The default status page shows every enabled monitor, so it's not available at all above this many of them
    const val DEFAULT_PAGE_MAX_MONITORS = 500
}
