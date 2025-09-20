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
    const val STATUS_PAGES_405_REASON =
        "Status pages are in read-only mode, because they are loaded from a YAML config file"
}
