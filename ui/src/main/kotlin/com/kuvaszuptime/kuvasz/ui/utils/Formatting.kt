package com.kuvaszuptime.kuvasz.ui.utils

import java.math.RoundingMode
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal fun OffsetDateTime.toDateTimeString(): String =
    this.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))

internal fun LocalDate.toDateTimeString(): String =
    this.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))

internal fun OffsetDateTime.toDateTimeStringWithZone(): String =
    this.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z"))

internal fun String.abbreviate(maxLength: Int): String {
    return if (this.length > maxLength) {
        this.take(maxLength) + "..."
    } else {
        this
    }
}

internal fun String.urlEncode() = URLEncoder.encode(this, StandardCharsets.UTF_8)

internal fun Double.formatAsPercentage(fractionDigits: Int = 2): String =
    (this * 100.toDouble()).toBigDecimal().setScale(fractionDigits, RoundingMode.FLOOR).toString() + "%"
