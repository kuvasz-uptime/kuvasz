package com.kuvaszuptime.kuvasz.models.dto

internal object Validation {
    const val MIN_UPTIME_CHECK_INTERVAL = 60L
    const val URI_REGEX = "^(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]"
}
