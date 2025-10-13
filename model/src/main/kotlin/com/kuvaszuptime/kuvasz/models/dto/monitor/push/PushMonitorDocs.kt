package com.kuvaszuptime.kuvasz.models.dto.monitor.push

object PushMonitorDocs {
    const val ID = "Unique identifier of the monitor"
    const val NAME = "Unique name for the monitor, e.g., 'My Website Monitor'"
    const val HEARTBEAT_INTERVAL = "The interval in seconds at which the monitor expects to receive heartbeats"
    const val GRACE_PERIOD = "The grace period in seconds after the heartbeat interval during which a missed " +
        "heartbeat will not mark the monitor as DOWN"
    const val CLIENT_SECRET = "The unique client secret used to identify & authenticate heartbeats"
    const val ENABLED = "Whether the monitor is enabled. If false, the monitor will not perform checks."
    const val CREATED_AT = "The creation timestamp of the monitor"
    const val UPDATED_AT = "The last updated timestamp of the monitor"
    const val UPTIME_STATUS =
        "The current uptime status of the monitor. If it's null, the monitor has not received a heartbeat yet."
    const val UPTIME_STATUS_STARTED_AT = "The timestamp when the uptime status was last changed"
    const val LAST_HEARTBEAT = "The timestamp of the last successful heartbeat"
    const val LAST_UPTIME_CHECK = "The timestamp when the last uptime check was performed"
    const val NEXT_EXPECTED_HEARTBEAT = "The timestamp until which the next heartbeat is expected"
    const val UPTIME_ERROR = "The error message if the last uptime check failed"
    const val INTEGRATIONS =
        "List of integrations explicitly assigned to the monitor, e.g. \"email:my-email-notification\""
    const val EFFECTIVE_INTEGRATIONS =
        "List of integrations that are effective for the monitor, including global integrations"
    const val MONITORS_405_REASON =
        "Push monitors are in read-only mode, because they are loaded from a YAML config file"
    const val STATUS_PAGES = "List of slugs of the status pages the monitor is explicitly assigned to"
    const val EXPLICIT_FAILURE_MESSAGE = "The optional, explicit error that is signaled manually for a push monitor"
}
