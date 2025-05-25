package com.kuvaszuptime.kuvasz.services.ui

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class TimeAgoFilterTest : StringSpec({

    "should return null if the input is null" {
        val result = TimeAgoFilter.apply(null, null, null, null, 0)
        result shouldBe null
    }

    "should return null if the input is not an OffsetDateTime" {
        val result = TimeAgoFilter.apply("not a date", null, null, null, 0)
        result shouldBe null
    }

    "should return a time ago string for a valid OffsetDateTime" {
        val date = OffsetDateTime.now().minusDays(1)
        val result = TimeAgoFilter.apply(date, null, null, null, 0)
        result shouldBe "a day ago"
    }
})
