package com.kuvaszuptime.kuvasz.models.dto

object Validation {
    const val MIN_UPTIME_CHECK_INTERVAL = 5L
    const val URI_REGEX = "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    const val MAX_RESPONSE_TIME_THRESHOLD_MILLIS = 30000L
    const val SLUG_REGEX = "^[a-z0-9_-]{1,50}$"
    const val MIN_HEARTBEAT_INTERVAL = 10L
    const val MIN_CLIENT_SECRET_LENGTH = 36
    const val MIN_PACKET_COUNT = 1L
    const val MAX_PACKET_COUNT = 10L
    const val MIN_TIMEOUT_SECONDS = 1L
    const val MAX_TIMEOUT_SECONDS = 30L
    const val MIN_PACKET_LOSS_THRESHOLD = 1L
    const val MAX_PACKET_LOSS_THRESHOLD = 100L
}

object ValidationMessages {
    const val WELL_FORMED_JSON_STRING = "The provided string must be a well-formed JSON"
    const val APP_CONFIG_EVENT_RETENTION_DAYS_MIN = "Event data retention must be at least {value} days"
    const val APP_CONFIG_LATENCY_RETENTION_DAYS_MIN = "Latency data retention must be at least {value} days"
    const val APP_CONFIG_HTTP_CHECK_TIMEOUT_MAX = "Read timeout for HTTP checks cannot be higher than {value} seconds"
    const val VALID_HEADER_NAMES = "All header names must be valid HTTP tokens as defined by RFC 9110, containing " +
        "one or more letters, digits, or the following symbols: ! # $ % & ' * + - . ^ _ ` | ~"
}

object IntegrationValidationMessages {
    const val SMTP_HOST_NOT_BLANK = "SMTP host must not be blank"
    const val SMTP_PORT_NOT_NULL = "SMTP port must not be null"
    const val PAGERDUTY_KEY_NOT_BLANK = "PagerDuty integration key must not be blank"
    const val EMAIL_INTEGRATION_TO_NOT_BLANK = "Email integration 'to' address must not be blank"
    const val EMAIL_INTEGRATION_FROM_NOT_BLANK = "Email integration 'from' address must not be blank"
    const val SLACK_WEBHOOK_URL_NOT_BLANK = "Slack integration webhook URL must not be blank"
    const val DISCORD_WEBHOOK_URL_NOT_BLANK = "Discord integration webhook URL must not be blank"
    const val TELEGRAM_CHAT_ID_NOT_BLANK = "Telegram integration chat ID must not be blank"
    const val TELEGRAM_BOT_TOKEN_NOT_BLANK = "Telegram integration bot token must not be blank"
    const val WEBHOOK_URL_NOT_BLANK = "Webhook integration URL must not be blank"
}

object MonitorValidationMessages {
    const val NAME_NOT_BLANK = "Monitor name must not be blank"
    const val URL_NOT_NULL = "URL must not be null"
    const val URL_PATTERN = "URL must be a valid HTTP(S) URI"
    const val UPTIME_CHECK_INTERVAL_NOT_NULL = "Uptime check interval must not be null"
    const val UPTIME_CHECK_INTERVAL_MIN = "Uptime check interval must be at least {value} seconds"
    const val HEARTBEAT_INTERVAL_NOT_NULL = "Heartbeat interval must not be null"
    const val HEARTBEAT_INTERVAL_MIN = "Heartbeat interval must be at least {value} seconds"
    const val GRACE_PERIOD_NOT_NULL = "Grace period must not be null"
    const val GRACE_PERIOD_POSITIVE_OR_ZERO = "Grace period must be greater than or equal to 0 seconds"
    const val SSL_EXPIRY_THRESHOLD_NOT_NULL = "SSL expiry threshold must not be null"
    const val SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO = "SSL expiry threshold must be greater than or equal to 0 days"
    const val RESPONSE_TIME_THRESHOLD_POSITIVE = "Response time threshold must be greater than 0 milliseconds"
    const val RESPONSE_TIME_THRESHOLD_MAX = "Response time threshold must be less than or equal to {value} milliseconds"
    const val SUPPORTED_STATUS_CODES = "All status codes must be valid HTTP status codes between 100 and 499"
    const val CLIENT_SECRET_NOT_NULL = "Client secret must not be null"
    const val CLIENT_SECRET_NOT_BLANK = "Client secret must not be blank"
    const val CLIENT_SECRET_MIN_LENGTH = "Client secret must be at least {min} characters long"
    const val FAILURE_COUNT_THRESHOLD_NOT_NULL = "Failure count threshold must not be null"
    const val FAILURE_COUNT_THRESHOLD_POSITIVE = "Failure count threshold must be greater than 0"
    const val HOST_NOT_BLANK = "Host must not be blank"
    const val PACKET_COUNT_NOT_NULL = "Packet count must not be null"
    const val PACKET_COUNT_MIN = "Packet count must be at least {value}"
    const val PACKET_COUNT_MAX = "Packet count must be at most {value}"
    const val TIMEOUT_SECONDS_NOT_NULL = "Timeout must not be null"
    const val TIMEOUT_SECONDS_MIN = "Timeout must be at least {value} second(s)"
    const val TIMEOUT_SECONDS_MAX = "Timeout must be at most {value} seconds"
    const val PACKET_LOSS_THRESHOLD_NOT_NULL = "Packet loss threshold must not be null"
    const val PACKET_LOSS_THRESHOLD_MIN = "Packet loss threshold must be at least {value}%"
    const val PACKET_LOSS_THRESHOLD_MAX = "Packet loss threshold must be at most {value}%"
}

object StatusPageValidationMessages {
    const val TITLE_NOT_BLANK = "Status page title must not be blank"
    const val SLUG_NOT_BLANK = "Status page slug must not be blank"
    const val SLUG_PATTERN = "Status page slug must be 1-50 characters long and can only contain " +
        "letters, numbers, hyphens, and underscores"
}

object MaintenanceWindowValidationMessages {
    const val NAME_NOT_BLANK = "Maintenance window name must not be blank"
    const val CRON_INVALID = "The cron expression is invalid"
    const val DURATION_INVALID = "The duration must be a positive ISO-8601 duration string (e.g. 'PT1H30M')"
    const val SCHEDULE_INVALID = "A maintenance window must be either manual (no 'cron' and no 'start'), " +
        "cron-based ('cron' + 'duration'), or single ('start' + 'duration'). " +
        "'cron' and 'start' are mutually exclusive, and both 'cron' and 'start' require a 'duration'."
}
