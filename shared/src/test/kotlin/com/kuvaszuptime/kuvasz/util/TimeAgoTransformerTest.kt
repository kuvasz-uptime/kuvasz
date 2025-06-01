package com.kuvaszuptime.kuvasz.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

class TimeAgoTransformerTest : StringSpec({
    "should return 'just now' for the current time" {
        val now = OffsetDateTime.now()
        now.timeAgo(now) shouldBe "just now"
    }

    "should return '5 seconds ago' for 5 seconds in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusSeconds(5)
        past.timeAgo(now) shouldBe "5 seconds ago"
    }

    "should return 'in 10 seconds' for 10 seconds in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusSeconds(10)
        future.timeAgo(now) shouldBe "in 10 seconds"
    }

    "should return 'a minute ago' for 1 minute in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusMinutes(1)
        past.timeAgo(now) shouldBe "a minute ago"
    }

    "should return 'in a minute' for 1 minute in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusMinutes(1)
        future.timeAgo(now) shouldBe "in a minute"
    }

    "should return '2 minutes ago' for 2 minutes in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusMinutes(2)
        past.timeAgo(now) shouldBe "2 minutes ago"
    }

    "should return 'in 2 minutes' for 2 minutes in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusMinutes(2)
        future.timeAgo(now) shouldBe "in 2 minutes"
    }

    "should return 'an hour ago' for 1 hour in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusHours(1)
        past.timeAgo(now) shouldBe "an hour ago"
    }

    "should return 'in an hour' for 1 hour in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusHours(1)
        future.timeAgo(now) shouldBe "in an hour"
    }

    "should return '2 hours ago' for 2 hours in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusHours(2)
        past.timeAgo(now) shouldBe "2 hours ago"
    }

    "should return 'in 2 hours' for 2 hours in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusHours(2)
        future.timeAgo(now) shouldBe "in 2 hours"
    }

    "should return 'a day ago' for 1 day in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusDays(1)
        past.timeAgo(now) shouldBe "a day ago"
    }

    "should return 'in a day' for 1 day in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusDays(1)
        future.timeAgo(now) shouldBe "in a day"
    }

    "should return '2 days ago' for 2 days in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusDays(2)
        past.timeAgo(now) shouldBe "2 days ago"
    }

    "should return 'in 2 days' for 2 days in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusDays(2)
        future.timeAgo(now) shouldBe "in 2 days"
    }

    "should return 'a week ago' for 1 week in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusWeeks(1)
        past.timeAgo(now) shouldBe "a week ago"
    }

    "should return 'in a week' for 1 week in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusWeeks(1)
        future.timeAgo(now) shouldBe "in a week"
    }

    "should return '2 weeks ago' for 2 weeks in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusWeeks(2)
        past.timeAgo(now) shouldBe "2 weeks ago"
    }

    "should return 'in 2 weeks' for 2 weeks in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusWeeks(2)
        future.timeAgo(now) shouldBe "in 2 weeks"
    }

    "should return 'a month ago' for 1 month in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusMonths(1)
        past.timeAgo(now) shouldBe "a month ago"
    }

    "should return 'in a month' for 1 month in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusMonths(1)
        future.timeAgo(now) shouldBe "in a month"
    }

    "should return '2 months ago' for 2 months in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusMonths(2)
        past.timeAgo(now) shouldBe "2 months ago"
    }

    "should return 'in 2 months' for 2 months in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusMonths(2)
        future.timeAgo(now) shouldBe "in 2 months"
    }

    "should return 'a year ago' for 1 year in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusYears(1)
        past.timeAgo(now) shouldBe "a year ago"
    }

    "should return 'in a year' for 1 year in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusYears(1)
        future.timeAgo(now) shouldBe "in a year"
    }

    "should return '2 years ago' for 2 years in the past" {
        val now = OffsetDateTime.now()
        val past = now.minusYears(2)
        past.timeAgo(now) shouldBe "2 years ago"
    }

    "should return 'in 2 years' for 2 years in the future" {
        val now = OffsetDateTime.now()
        val future = now.plusYears(2)
        future.timeAgo(now) shouldBe "in 2 years"
    }
})
