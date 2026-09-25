package com.kuvaszuptime.kuvasz.models.dto.monitor.docker

object DockerMonitorDefaults {
    const val MONITOR_ENABLED = true
    const val TIMEOUT_MS = 5000
    const val FAILURE_COUNT_THRESHOLD = 1L
    const val METRICS_HISTORY_ENABLED = false

    // Deviates from MonitorDefaults.IGNORE_CONNECTIVITY_CHECK: a container reached over a local socket does not care
    // whether Kuvasz itself has internet access, and suppressing its checks during an outage would hide real
    // failures. Operators watching a remote daemon can flip it.
    const val IGNORE_CONNECTIVITY_CHECK = true
}
