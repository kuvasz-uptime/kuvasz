package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

object TcpMonitorDocs {
    const val HOST = "The hostname or IP address to connect to"
    const val PORT = "The TCP port to connect to (1-65535)"
    const val TIMEOUT_MS = "The connection timeout in milliseconds (1-30000)"
    const val LATENCY_THRESHOLD_MS =
        "Optional connect-latency threshold in milliseconds. If set, the check is considered DOWN when the " +
            "connection takes longer than this value."
    const val METRICS_HISTORY_ENABLED = "Whether metrics history is enabled for the monitor"
    const val MONITORS_405_REASON =
        "TCP monitors are in read-only mode, because they are loaded from a YAML config file"
}
