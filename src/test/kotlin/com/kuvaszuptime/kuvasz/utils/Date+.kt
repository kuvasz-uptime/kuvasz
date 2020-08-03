package com.kuvaszuptime.kuvasz.utils

import arrow.core.Option
import com.kuvaszuptime.kuvasz.util.toDurationString
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class DateTest : BehaviorSpec() {
    init {
        given("Option<Duration>.toDurationString() method") {
            `when`("the receiver is an Empty") {
                val receiver = Option.empty<Duration>()
                then("it should return Empty") {
                    val result = receiver.toDurationString()
                    result.isEmpty() shouldBe true
                }
            }

            `when`("the receiver is a Some") {
                val receiver = Option.just(100_000.toDuration(DurationUnit.SECONDS))
                then("it should return the correct string in a Some") {
                    val result = receiver.toDurationString()
                    result.exists { it == "1 day(s), 3 hour(s), 46 minute(s), 40 second(s)" } shouldBe true
                }
            }
        }
    }
}
