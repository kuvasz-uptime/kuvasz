package com.kuvaszuptime.kuvasz.utils

import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DateTest : BehaviorSpec() {
    init {
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
