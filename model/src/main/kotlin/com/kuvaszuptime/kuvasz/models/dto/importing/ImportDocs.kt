package com.kuvaszuptime.kuvasz.models.dto.importing

object ImportDocs {
    const val DRY_RUN = "Whether the import was a dry run. When true, no changes were applied and the lists below " +
        "only describe what would have happened."
    const val RECEIVED_CNT = "Number of items found in the uploaded backup file"
    const val IMPORTED_MONITORS = "Identifiers of the monitors that were (or would be) imported"
    const val DELETED_MONITORS = "Identifiers of the monitors that were (or would be) deleted, because they are " +
        "absent from the backup file"
    const val IMPORTED_STATUS_PAGES = "Titles of the status pages that were (or would be) imported"
    const val DELETED_STATUS_PAGES = "Titles of the status pages that were (or would be) deleted, because they are " +
        "absent from the backup file"
    const val IMPORTED_MAINTENANCE_WINDOWS = "Names of the maintenance windows that were (or would be) imported"
    const val DELETED_MAINTENANCE_WINDOWS = "Names of the maintenance windows that were (or would be) deleted, " +
        "because they are absent from the backup file"
    const val IGNORED_MONITORS = "Referenced monitors that were dropped during validation, because they do not exist"
    const val IGNORED_INTEGRATIONS = "Referenced integrations that were dropped during validation, because they are " +
        "not configured"
    const val PER_TYPE_RESULTS = "Import results broken down by monitor type"
    const val MONITOR_TYPE = "The monitor type this result belongs to"
}
