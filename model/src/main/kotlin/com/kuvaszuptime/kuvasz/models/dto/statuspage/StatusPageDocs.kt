package com.kuvaszuptime.kuvasz.models.dto.statuspage

object StatusPageDocs {
    const val ID = "Unique identifier of the status page"
    const val TITLE = "Title of the status page"
    const val SLUG = "Unique slug of the status page, used in the URL"
    const val ENABLED = "Whether the status page is enabled"
    const val MONITORS = "Set of monitor IDs that are included in the status page"
    const val CREATED_AT = "Timestamp of when the status page was created"
    const val UPDATED_AT = "Timestamp of when the status page was last updated"
    const val STATUS_PAGES_405_REASON =
        "Status pages are in read-only mode, because they are loaded from a YAML config file"
}
