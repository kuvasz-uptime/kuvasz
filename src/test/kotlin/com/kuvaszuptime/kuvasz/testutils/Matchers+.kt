package com.kuvaszuptime.kuvasz.testutils

import io.kotest.matchers.booleans.shouldBeTrue
import java.time.OffsetDateTime
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

infix fun OffsetDateTime.shouldBe(otherOffsetDateTime: OffsetDateTime) =
    this.roundToMicros().isEqual(otherOffsetDateTime.roundToMicros()).shouldBeTrue()

@Suppress("MagicNumber")
private fun OffsetDateTime.roundToMicros(): OffsetDateTime {
    val nanos = get(ChronoField.NANO_OF_SECOND)
    val roundedMicros = (nanos / 1000.0).roundToLong()
    return truncatedTo(ChronoUnit.SECONDS).plus(roundedMicros, ChronoUnit.MICROS)
}
