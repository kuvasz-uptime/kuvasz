package com.kuvaszuptime.kuvasz.models.dto.statuspage

object StatusPageDocs {
    const val ID = "Unique identifier of the status page"
    const val TITLE = "Title of the status page"
    const val SLUG = "Unique slug of the status page, used in the URL"
    const val CUSTOM_LOGO_URL = "The absolute URL of the custom logo displayed on the status page"
    const val CUSTOM_FAVICON_URL = "The absolute URL of the custom favicon displayed on the status page. " +
        "Only PNG format is supported."
    const val PUBLIC = "Whether the status page is publicly accessible"
    const val MONITORS = "Set of monitor IDs that are included in the status page"
    const val CREATED_AT = "Timestamp of when the status page was created"
    const val UPDATED_AT = "Timestamp of when the status page was last updated"
    const val SYSTEM_STATUS = "The cumulated status of the monitors included in the status page"
    const val STATUS_GENERATED_AT = "Timestamp of when the status page was generated"
    const val MONITOR_DETAILS =
        "The details of the monitors included in the status page, such as their uptime status and uptime ratio"
    const val ACTIVE_MAINTENANCE_WINDOWS =
        "The maintenance windows that are shown on the status page and currently active for at least one of its monitors"
    const val UPCOMING_MAINTENANCE_WINDOWS =
        "The maintenance windows that are shown on the status page and going to start within the next 24 hours for " +
            "at least one of its monitors"
    const val STATUS_PAGES_405_REASON =
        "Status pages are in read-only mode, because they are loaded from a YAML config file"
}
