package com.kuvaszuptime.kuvasz.models.dto.monitor.http

object HttpMonitorDocs {
    const val URL = "The URL that is monitored, must include the protocol (http(s)://) as well."
    const val SENSITIVE_URL =
        "Whether the URL is sensitive (e.g. contains a secret token). If true, the URL will be masked in integrations, logs and metrics to protect sensitive information."
    const val SSL_CHECK_ENABLED = "Whether the monitor checks the SSL certificate for expiry and validity."
    const val SSL_STATUS = "The current SSL status of the monitor. If it's null, the monitor has not been checked yet."
    const val SSL_STATUS_STARTED_AT = "The timestamp when the SSL status was last changed"
    const val LAST_SSL_CHECK = "The timestamp when the last SSL check was performed"
    const val NEXT_SSL_CHECK = "The timestamp when the next SSL check is scheduled"
    const val SSL_ERROR = "The error message if the last SSL check failed"
    const val SSL_EXPIRY_THRESHOLD = "The threshold in days for SSL certificate expiry checks"
    const val SSL_VALID_UNTIL = "The timestamp until which the SSL certificate is valid"
    const val REQUEST_METHOD = "The HTTP method used for the uptime check"
    const val LATENCY_HISTORY_ENABLED = "Whether latency history is enabled for the monitor"
    const val FORCE_NO_CACHE = "Whether to send a force no-cache headers in the request"
    const val FOLLOW_REDIRECTS = "Whether to follow redirects during the uptime check"
    const val EXPECTED_STATUS_CODES =
        "List of expected HTTP status codes for the monitor. If the response code is not in this list, the monitor " +
            "will be marked as DOWN. By default, every 2xx status will be considered as UP. 1xx, 2xx, 3xx and 4xx " +
            "codes are supported."
    const val RESPONSE_TIME_THRESHOLD =
        "The threshold in milliseconds for the response time. If the response time exceeds this value, the monitor " +
            "will be marked as DOWN."
    const val EXPECTED_KEYWORD =
        "A keyword that is expected to be present in the response body. If the keyword is not found, the monitor " +
            "will be marked as DOWN."
    const val EXPECTED_KEYWORD_CASE_SENSITIVE =
        "Whether the expected keyword check is case-sensitive. If false, the check will be case-insensitive."
    const val EXPECTED_KEYWORD_NEGATED =
        "Whether the expected keyword check is negated. If true, the monitor will be marked as DOWN if the keyword " +
            "is found in the response body."
    const val REQUEST_HEADERS =
        "Custom HTTP headers to be sent with the request. It is a map of header names to values, null values are " +
            "not allowed."
    const val EXPECTED_HEADERS =
        "Expected HTTP headers in the response. If any of these headers are missing, the monitor will be marked as " +
            "DOWN. It is a map of header names to values, null values are not allowed. The check will be " +
            "case-insensitive for the header names, but the values must match exactly"
    const val REQUEST_BODY = "The body of the request to be sent, which is used for POST, PATCH and PUT requests. " +
        "Currently only valid JSON bodies are supported."
    const val MONITORS_405_REASON =
        "HTTP monitors are in read-only mode, because they are loaded from a YAML config file"
}
