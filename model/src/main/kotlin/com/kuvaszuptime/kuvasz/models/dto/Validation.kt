package com.kuvaszuptime.kuvasz.models.dto

object Validation {
    const val MIN_UPTIME_CHECK_INTERVAL = 5L
    const val URI_REGEX = "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    const val MAX_RESPONSE_TIME_THRESHOLD_MILLIS = 30000L
}

object ValidationMessages {
    const val NAME_NOT_BLANK = "Monitor name must not be blank"
    const val URL_NOT_NULL = "URL must not be null"
    const val URL_PATTERN = "URL must be a valid HTTP(S) URI"
    const val UPTIME_CHECK_INTERVAL_NOT_NULL = "Uptime check interval must not be null"
    const val UPTIME_CHECK_INTERVAL_MIN = "Uptime check interval must be at least {value} seconds"
    const val SSL_EXPIRY_THRESHOLD_NOT_NULL = "SSL expiry threshold must not be null"
    const val SSL_EXPIRY_THRESHOLD_POSITIVE_OR_ZERO = "SSL expiry threshold must be greater than or equal to 0 days"
    const val RESPONSE_TIME_THRESHOLD_POSITIVE = "Response time threshold must be greater than 0 milliseconds"
    const val RESPONSE_TIME_THRESHOLD_MAX = "Response time threshold must be less than or equal to {value} milliseconds"
    const val SUPPORTED_STATUS_CODES = "All status codes must be valid HTTP status codes between 100 and 499"
    const val VALID_HEADER_NAMES = "All header names must be valid HTTP header names, containing only " +
        "alphanumeric characters and hyphens, starting with a letter"
    const val WELL_FORMED_JSON_STRING = "The provided string must be a well-formed JSON"
    const val APP_CONFIG_EVENT_RETENTION_DAYS_MIN = "Event data retention must be at least {value} days"
    const val APP_CONFIG_LATENCY_RETENTION_DAYS_MIN = "Latency data retention must be at least {value} days"
    const val SMTP_HOST_NOT_BLANK = "SMTP host must not be blank"
    const val SMTP_PORT_NOT_NULL = "SMTP port must not be null"
    const val PAGERDUTY_KEY_NOT_BLANK = "PagerDuty integration key must not be blank"
    const val EMAIL_INTEGRATION_TO_NOT_BLANK = "Email integration 'to' address must not be blank"
    const val EMAIL_INTEGRATION_FROM_NOT_BLANK = "Email integration 'from' address must not be blank"
    const val SLACK_WEBHOOK_URL_NOT_BLANK = "Slack integration webhook URL must not be blank"
    const val DISCORD_WEBHOOK_URL_NOT_BLANK = "Discord integration webhook URL must not be blank"
    const val TELEGRAM_CHAT_ID_NOT_BLANK = "Telegram integration chat ID must not be blank"
    const val TELEGRAM_BOT_TOKEN_NOT_BLANK = "Telegram integration bot token must not be blank"
}
