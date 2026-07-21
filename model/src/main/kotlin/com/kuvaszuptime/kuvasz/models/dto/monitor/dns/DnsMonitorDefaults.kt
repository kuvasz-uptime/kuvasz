package com.kuvaszuptime.kuvasz.models.dto.monitor.dns

import com.kuvaszuptime.kuvasz.jooq.enums.DnsResponseCode
import com.kuvaszuptime.kuvasz.jooq.enums.DnsTransport

object DnsMonitorDefaults {
    const val MONITOR_ENABLED = true
    const val RESOLVER_PORT = 53
    val TRANSPORT = DnsTransport.UDP
    val EXPECTED_RESPONSE_CODE = DnsResponseCode.NOERROR
    const val DRIFT_DETECTION_ENABLED = false
    const val TIMEOUT_MS = 5000
    const val FAILURE_COUNT_THRESHOLD = 1L
    const val METRICS_HISTORY_ENABLED = true
}
