package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

object DnsMonitorDefaults {
    const val MONITOR_ENABLED = true
    const val RESOLVER_PORT = 53
    const val TRANSPORT = "UDP"
    const val EXPECTED_RESPONSE_CODE = "NOERROR"
    const val DRIFT_DETECTION_ENABLED = false
    const val TIMEOUT_MS = 5000
    const val FAILURE_COUNT_THRESHOLD = 1L
    const val METRICS_HISTORY_ENABLED = true
}
