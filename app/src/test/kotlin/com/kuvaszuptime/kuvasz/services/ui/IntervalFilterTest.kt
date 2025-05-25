package com.kuvaszuptime.kuvasz.services.ui

import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class IntervalFilterTest : StringSpec({

    "should return null if the input is null" {
        val result = IntervalFilter.apply(null, mapOf("until" to getCurrentTimestamp()), null, null, 0)
        result shouldBe null
    }

    "should return null if the input is not an OffsetDateTime" {
        val result = IntervalFilter.apply("not a date", mapOf("until" to getCurrentTimestamp()), null, null, 0)
        result shouldBe null
    }

    "should return an interval string for a valid OffsetDateTime" {
        val date = OffsetDateTime.now().minusDays(1)
        val result = TimeAgoFilter.apply(date, null, null, null, 0)
        result shouldBe "a day ago"
    }
})
