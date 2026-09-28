package com.kuvaszuptime.kuvasz.services.check

import com.kuvaszuptime.kuvasz.util.toOffsetDateTime
import java.time.Instant
import java.time.OffsetDateTime
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

fun ScheduledFuture<*>?.gracefulCancel() {
    this?.cancel(false)
}

fun ScheduledFuture<*>.getNextCheck(): OffsetDateTime {
    val nextCheckEpoch = System.currentTimeMillis() + this.getDelay(TimeUnit.MILLISECONDS)
    return Instant.ofEpochMilli(nextCheckEpoch).toOffsetDateTime()
}
