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
    const val DISPLAY_CATEGORIES = "Whether the monitors are displayed grouped into their categories on the page. " +
        "It only affects the rendering: the categories still select the monitors of the page, and the per-category " +
        "statuses are calculated and returned either way"
    const val THEME_BASE = "The gray palette of the status page"
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
    const val HISTORY_DAYS = "The number of days the uptime status history of the monitors covers: 30 days, or less, " +
        "if the event data retention period is shorter than that"
    const val HISTORY_DATE = "The date of the data point"
    const val HISTORY_OUTAGE_COUNT = "The number of incidents that were open on the given date, null if there is no " +
        "data for that date (e.g. the monitor didn't exist yet)"
    const val HISTORY_DOWNTIME_SECONDS = "The total downtime on the given date in seconds, null if there is no data " +
        "for that date"
    const val HISTORY_INCIDENTS = "The incidents that were open on the given date, an incident spanning multiple " +
        "days is listed on each of them. Only the latest " + StatusPageDefaults.MAX_INCIDENTS_PER_MONITOR +
        " incidents of the monitor are listed in its whole history, see incidentCount for the number of every one " +
        "of them. outageCnt always counts every incident of the date."
    const val INCIDENT_COUNT = "The number of incidents of the monitor in its whole uptime status history. It can " +
        "be more than the number of the incidents listed on its dates, as only the latest " +
        StatusPageDefaults.MAX_INCIDENTS_PER_MONITOR + " of them are listed."
    const val INCIDENT_ID = "The ID of the incident, the same on every day the incident spans. It's only unique " +
        "among the incidents of the monitors of the same type, so it identifies an incident together with the " +
        "`type` of its monitor"
    const val INCIDENT_STARTED_AT = "When the incident started"
    const val INCIDENT_ENDED_AT = "When the incident was resolved, null if it's ongoing"
    const val INCIDENT_STATUS = "Whether the incident is still ongoing or resolved"
    const val INCIDENT_NOTES = "The public notes of the incident, ordered by their timestamp"
    const val NOTE_STATUS = "The state of the investigation the note describes"
    const val NOTE_CONTENT = "The content of the note, as plain text"
    const val NOTE_TIMESTAMP = "The timestamp of the note, which is either given explicitly, or the creation date of " +
        "the note"
    const val STATUS_PAGES_405_REASON =
        "Status pages are in read-only mode, because they are loaded from a YAML config file"
}
