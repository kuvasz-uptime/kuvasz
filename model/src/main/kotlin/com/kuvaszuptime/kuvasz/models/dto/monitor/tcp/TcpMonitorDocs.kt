package com.kuvaszuptime.kuvasz.models.dto.monitor.tcp

object TcpMonitorDocs {
    const val HOST = "The hostname or IP address to connect to"
    const val PORT = "The TCP port to connect to (1-65535)"
    const val TIMEOUT_MS = "The connection timeout in milliseconds (1-30000)"
    const val LATENCY_THRESHOLD_MS =
        "Optional connect-latency threshold in milliseconds. If set, the check is considered DOWN when the " +
            "connection takes longer than this value."
    const val PROXY =
        "The name of a proxy defined in the configuration, that the uptime checks of the monitor are routed " +
            "through. The proxy resolves the host, and the latency covers the connection to the proxy and the " +
            "establishment of the tunnel. If null, the monitor is checked over a direct connection."
    const val METRICS_HISTORY_ENABLED = "Whether metrics history is enabled for the monitor"
    const val MONITORS_405_REASON =
        "TCP monitors are in read-only mode, because they are loaded from a YAML config file"
}
