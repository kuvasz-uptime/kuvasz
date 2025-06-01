package com.kuvaszuptime.kuvasz.ui.utils

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal fun OffsetDateTime.toDateTimeString(): String =
    this.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))

internal fun String.abbreviate(maxLength: Int): String {
    return if (this.length > maxLength) {
        this.take(maxLength) + "..."
    } else {
        this
    }
}
