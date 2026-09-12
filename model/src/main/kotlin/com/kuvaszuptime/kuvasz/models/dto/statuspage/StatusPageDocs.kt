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
    const val CATEGORIES = "Set of monitor categories that are included in the status page. It is additive with " +
        "'monitors': the page shows the monitors listed there plus every monitor belonging to one of these " +
        "categories. A category that is not in use by any monitor is kept, it simply contributes nothing."
    const val CREATED_AT = "Timestamp of when the status page was created"
    const val UPDATED_AT = "Timestamp of when the status page was last updated"
    const val SYSTEM_STATUS = "The cumulated status of the monitors included in the status page"
    const val STATUS_GENERATED_AT = "Timestamp of when the status page was generated"
    const val MONITOR_DETAILS =
        "The details of the monitors included in the status page, such as their uptime status and uptime ratio"
    const val CATEGORY_STATUS =
        "The aggregated status of every monitor category on the status page, ordered by the category name, with the " +
            "uncategorized monitors last. Empty if none of the monitors is categorized"
    const val CATEGORY = "The category of the monitors, or null for the ones that are not categorized"
    const val CATEGORY_SYSTEM_STATUS = "The cumulated status of the monitors belonging to the category"
    const val ACTIVE_MAINTENANCE_WINDOWS =
        "The maintenance windows that are shown on the status page and currently active for at least one of its monitors"
    const val UPCOMING_MAINTENANCE_WINDOWS =
        "The maintenance windows that are shown on the status page and going to start within the next 24 hours for " +
            "at least one of its monitors"
    const val STATUS_PAGES_405_REASON =
        "Status pages are in read-only mode, because they are loaded from a YAML config file"
}
