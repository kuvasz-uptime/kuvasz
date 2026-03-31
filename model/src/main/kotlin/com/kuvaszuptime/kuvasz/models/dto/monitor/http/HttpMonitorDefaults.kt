package com.kuvaszuptime.kuvasz.models.dto.monitor.http

object HttpMonitorDefaults {
    const val MONITOR_ENABLED = true
    const val SSL_CHECK_ENABLED = false
    const val SENSITIVE_URL = false
    const val REQUEST_METHOD = "GET"
    const val LATENCY_HISTORY_ENABLED = true
    const val FORCE_NO_CACHE = true
    const val FOLLOW_REDIRECTS = true
    const val SSL_EXPIRY_THRESHOLD_DAYS = 30
    const val FAILURE_COUNT_THRESHOLD = 1L
    const val EXPECTED_KEYWORD_CASE_SENSITIVE = false
    const val EXPECTED_KEYWORD_NEGATED = false
}
