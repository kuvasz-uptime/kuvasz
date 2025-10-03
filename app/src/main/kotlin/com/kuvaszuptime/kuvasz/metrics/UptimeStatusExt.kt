package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus

fun UptimeStatus.toLong() = when (this) {
    UptimeStatus.UP -> 1L
    UptimeStatus.DOWN -> 0L
}
