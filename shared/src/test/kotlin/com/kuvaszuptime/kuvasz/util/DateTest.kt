package com.kuvaszuptime.kuvasz.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DateTest : BehaviorSpec() {
    init {
        given("the getCurrentTimestamp() method") {
            `when`("the underlying clock has sub-microsecond precision") {
                val zone = ZoneId.of("UTC")
                val clock = Clock.fixed(Instant.parse("2026-08-21T10:15:30.123456789Z"), zone)
                then("it should truncate to whole microseconds to survive a PostgreSQL round-trip intact") {
                    getCurrentTimestamp(clock) shouldBe
                        OffsetDateTime.ofInstant(Instant.parse("2026-08-21T10:15:30.123456Z"), zone)
                }
            }

            `when`("it is called with the default system clock") {
                then("it should never carry sub-microsecond nanos") {
                    getCurrentTimestamp().nano % 1000 shouldBe 0
                }
            }
        }

        given("Duration?.toDurationString() method") {
            `when`("the receiver is null") {
                val receiver = null
                then("it should return null") {
                    receiver.toDurationString() shouldBe null
                }
            }

            `when`("the receiver is a Duration") {
                val receiver = 100_000.toDuration(DurationUnit.SECONDS)
                then("it should return the correct string") {
                    receiver.toDurationString() shouldBe "1 day(s), 3 hour(s), 46 minute(s), 40 second(s)"
                }
            }
        }
    }
}
