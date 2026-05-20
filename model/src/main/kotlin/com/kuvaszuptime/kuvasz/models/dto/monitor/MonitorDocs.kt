package com.kuvaszuptime.kuvasz.models.dto.monitor

object MonitorDocs {
    const val ID = "Unique identifier of the monitor"
    const val NAME = "Unique name for the monitor, e.g., 'My Server Monitor'"
    const val UPTIME_CHECK_INTERVAL = "The interval in seconds at which the monitor will be checked"
    const val FAILURE_COUNT_THRESHOLD = "The threshold for consecutive failures before the monitor is marked as DOWN"
    const val ENABLED = "Whether the monitor is enabled. If false, the monitor will not perform checks."
    const val INTEGRATIONS =
        "List of integrations explicitly assigned to the monitor, e.g. \"email:my-email-notification\""
    const val EFFECTIVE_INTEGRATIONS =
        "List of integrations that are effective for the monitor, including global integrations"
    const val CREATED_AT = "The creation timestamp of the monitor"
    const val UPDATED_AT = "The last updated timestamp of the monitor"
    const val UPTIME_STATUS =
        "The current uptime status of the monitor. If it's null, no check has completed yet."
    const val UPTIME_STATUS_STARTED_AT = "The timestamp when the uptime status was last changed"
    const val LAST_UPTIME_CHECK = "The timestamp when the last uptime check was performed"
    const val NEXT_UPTIME_CHECK = "The timestamp when the next uptime check is scheduled"
    const val UPTIME_ERROR = "The error message if the last uptime check failed"
    const val STATUS_PAGES = "List of slugs of the status pages the monitor is explicitly assigned to"
}
