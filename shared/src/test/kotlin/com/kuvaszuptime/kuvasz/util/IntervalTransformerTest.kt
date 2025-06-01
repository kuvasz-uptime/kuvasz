package com.kuvaszuptime.kuvasz.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import java.time.OffsetDateTime

class IntervalTransformerTest : StringSpec({
    "should return '1 second' for a 1-second interval" {
        val start = OffsetDateTime.now()
        val end = start.plusSeconds(1)
        start.durationBetween(end) shouldBe "1 second"
    }

    "should return '2 seconds' for a 2-second interval" {
        val start = OffsetDateTime.now()
        val end = start.plusSeconds(2)
        start.durationBetween(end) shouldBe "2 seconds"
    }

    "should return '1 minute' for a 1-minute interval" {
        val start = OffsetDateTime.now()
        val end = start.plusMinutes(1)
        start.durationBetween(end) shouldBe "1 minute"
    }

    "should return '2 minutes' for a 2-minute interval" {
        val start = OffsetDateTime.now()
        val end = start.plusMinutes(2)
        start.durationBetween(end) shouldBe "2 minutes"
    }

    "should return '1 hour' for a 1-hour interval" {
        val start = OffsetDateTime.now()
        val end = start.plusHours(1)
        start.durationBetween(end) shouldBe "1 hour"
    }

    "should return '2 hours' for a 2-hour interval" {
        val start = OffsetDateTime.now()
        val end = start.plusHours(2)
        start.durationBetween(end) shouldBe "2 hours"
    }

    "should return '1 day' for a 1-day interval" {
        val start = OffsetDateTime.now()
        val end = start.plusDays(1)
        start.durationBetween(end) shouldBe "1 day"
    }

    "should return '2 days' for a 2-day interval" {
        val start = OffsetDateTime.now()
        val end = start.plusDays(2)
        start.durationBetween(end) shouldBe "2 days"
    }

    "should return '1 day 1 hour' for a 1-day and 1-hour interval" {
        val start = OffsetDateTime.now()
        val end = start.plusDays(1).plusHours(1)
        start.durationBetween(end) shouldBe "1 day 1 hour"
    }

    "should return '1 hour 1 minute' for a 1-hour and 1-minute interval" {
        val start = OffsetDateTime.now()
        val end = start.plusHours(1).plusMinutes(1)
        start.durationBetween(end) shouldBe "1 hour 1 minute"
    }

    "should return '1 minute 1 second' for a 1-minute and 1-second interval" {
        val start = OffsetDateTime.now()
        val end = start.plusMinutes(1).plusSeconds(1)
        start.durationBetween(end) shouldBe "1 minute 1 second"
    }

    "should return '2 days 3 hours' for a 2-day and 3-hour interval" {
        val start = OffsetDateTime.now()
        val end = start.plusDays(2).plusHours(3)
        start.durationBetween(end) shouldBe "2 days 3 hours"
    }

    "should return an empty string for the same start and end time" {
        val start = OffsetDateTime.now()
        start.durationBetween(start).shouldBeEmpty()
    }

    "should limit the returned fragments at 2 elements" {
        val start = OffsetDateTime.now()
        val end = start.plusDays(2).plusHours(3).plusMinutes(4).plusSeconds(5)
        start.durationBetween(end) shouldBe "2 days 3 hours"

        val end2 = start.plusHours(1).plusMinutes(1).plusSeconds(1)
        start.durationBetween(end2) shouldBe "1 hour 1 minute"
    }
})
