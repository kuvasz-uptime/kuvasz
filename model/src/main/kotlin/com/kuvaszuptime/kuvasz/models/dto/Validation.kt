package com.kuvaszuptime.kuvasz.models.dto

object Validation {
    const val MIN_UPTIME_CHECK_INTERVAL = 5L
    const val URI_REGEX = "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
    const val MAX_RESPONSE_TIME_THRESHOLD_MILLIS = 30000L
}
