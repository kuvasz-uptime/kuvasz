package com.kuvaszuptime.kuvasz.models.dto.monitor.push

object PushMonitorDocs {
    const val HEARTBEAT_INTERVAL = "The interval in seconds at which the monitor expects to receive heartbeats"
    const val GRACE_PERIOD = "The grace period in seconds after the heartbeat interval during which a missed " +
        "heartbeat will not mark the monitor as DOWN"
    const val CLIENT_SECRET = "The unique client secret used to identify & authenticate heartbeats"
    const val LAST_HEARTBEAT = "The timestamp of the last successful heartbeat"
    const val NEXT_EXPECTED_HEARTBEAT = "The timestamp until which the next heartbeat is expected"
    const val MONITORS_405_REASON =
        "Push monitors are in read-only mode, because they are loaded from a YAML config file"
    const val EXPLICIT_FAILURE_MESSAGE = "The optional, explicit error that is signaled manually for a push monitor"
}
