package com.kuvaszuptime.kuvasz.util

import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import java.time.Duration

class SimpleIntervalFormatterTest : StringSpec({

    "Duration.formatAsSimpleInterval() should format the given duration correctly" {

        table(
            headers("duration", "expectedFormattedString"),
            row(Duration.ofSeconds(0), "0 second"),
            row(Duration.ofSeconds(1000), "16 minutes"),
            row(Duration.ofMinutes(61), "1 hour"),
            row(Duration.ofHours(25), "1 day"),
            row(Duration.ofDays(10), "10 days"),
            row(Duration.ofDays(1), "1 day"),
            row(Duration.ofHours(1), "1 hour"),
            row(Duration.ofMinutes(1), "1 minute"),
            row(Duration.ofSeconds(1), "1 second"),
            row(Duration.ofSeconds(45), "45 seconds"),
            row(Duration.ofMinutes(45), "45 minutes"),
            row(Duration.ofHours(5), "5 hours"),
            row(Duration.ofDays(5), "5 days"),
        ).forAll { duration, expectedFormattedString ->
            duration.formatAsSimpleInterval() shouldBe expectedFormattedString
        }
    }
})
