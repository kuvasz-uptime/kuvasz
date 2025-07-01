package com.kuvaszuptime.kuvasz.models.dto

object MonitorDocs {
    const val ID = "Unique identifier of the monitor"
    const val NAME = "Unique name for the monitor, e.g., 'My Website Monitor'"
    const val URL = "The URL that is monitored"
    const val UPTIME_CHECK_INTERVAL = "The interval in seconds at which the monitor checks for uptime"
    const val ENABLED = "Whether the monitor is enabled. If false, the monitor will not perform checks."
    const val SSL_CHECK_ENABLED = "Whether the monitor checks the SSL certificate for expiry and validity."
    const val CREATED_AT = "The creation timestamp of the monitor"
    const val UPDATED_AT = "The last updated timestamp of the monitor"
    const val UPTIME_STATUS =
        "The current uptime status of the monitor. If it's null, the monitor has not been checked yet."
    const val SSL_STATUS = "The current SSL status of the monitor. If it's null, the monitor has not been checked yet."
    const val UPTIME_STATUS_STARTED_AT = "The timestamp when the uptime status was last changed"
    const val SSL_STATUS_STARTED_AT = "The timestamp when the SSL status was last changed"
    const val LAST_UPTIME_CHECK = "The timestamp when the last uptime check was performed"
    const val LAST_SSL_CHECK = "The timestamp when the last SSL check was performed"
    const val NEXT_UPTIME_CHECK = "The timestamp when the next uptime check is scheduled"
    const val NEXT_SSL_CHECK = "The timestamp when the next SSL check is scheduled"
    const val UPTIME_ERROR = "The error message if the last uptime check failed"
    const val SSL_ERROR = "The error message if the last SSL check failed"
    const val SSL_EXPIRY_THRESHOLD = "The threshold in days for SSL certificate expiry checks"
    const val SSL_VALID_UNTIL = "The timestamp until which the SSL certificate is valid"
    const val REQUEST_METHOD = "The HTTP method used for the uptime check"
    const val LATENCY_HISTORY_ENABLED = "Whether latency history is enabled for the monitor"
    const val FORCE_NO_CACHE = "Whether to send a force no-cache headers in the request"
    const val FOLLOW_REDIRECTS = "Whether to follow redirects during the uptime check"
    const val INTEGRATIONS =
        "List of integrations explicitly assigned to the monitor, e.g. \"email:my-email-notification\""
    const val EFFECTIVE_INTEGRATIONS =
        "List of integrations that are effective for the monitor, including global integrations"
}

object IntegrationDocs {
    const val ID = "Unique, computed identifier of the integration, e.g. \"email:my-email-notification\""
    const val NAME = "Name of the integration. Must be unique in the context of type."
    const val TYPE = "Type of the integration"
    const val ENABLED = "Whether the integration is enabled"
    const val GLOBAL = "Whether the integration is global (applies to all monitors by default)"
}
