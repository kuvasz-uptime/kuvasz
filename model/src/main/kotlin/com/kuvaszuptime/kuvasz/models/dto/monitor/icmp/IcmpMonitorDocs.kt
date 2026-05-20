package com.kuvaszuptime.kuvasz.models.dto.monitor.icmp

object IcmpMonitorDocs {
    const val HOST = "The hostname or IP address to ping"
    const val PACKET_COUNT = "The number of ICMP packets to send per check (1-10)"
    const val TIMEOUT_SECONDS = "The per-ping timeout in seconds (1-30)"
    const val PACKET_LOSS_THRESHOLD =
        "The packet loss percentage (1-100) at or above which the check is considered DOWN. " +
            "Default 100 means all packets must fail to trigger DOWN."
    const val METRICS_HISTORY_ENABLED = "Whether metrics history is enabled for the monitor"
    const val MONITORS_405_REASON =
        "ICMP monitors are in read-only mode, because they are loaded from a YAML config file"
}
