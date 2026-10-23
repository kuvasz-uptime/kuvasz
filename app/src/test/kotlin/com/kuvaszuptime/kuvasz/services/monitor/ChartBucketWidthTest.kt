package com.kuvaszuptime.kuvasz.services.monitor

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.time.Duration

class ChartBucketWidthTest : BehaviorSpec({

    given("the chartBucketWidthOf() function") {

        `when`("it's called with the usual spans") {
            then("the width is the smallest round one that keeps the chart within about 1000 points") {
                mapOf(
                    Duration.ofMinutes(1) to Duration.ofSeconds(1),
                    Duration.ofHours(1) to Duration.ofSeconds(5),
                    Duration.ofHours(6) to Duration.ofSeconds(30),
                    Duration.ofHours(24) to Duration.ofMinutes(2),
                    Duration.ofDays(7) to Duration.ofMinutes(15),
                    Duration.ofDays(30) to Duration.ofHours(1),
                    Duration.ofDays(365) to Duration.ofHours(12),
                ).forEach { (span, width) ->
                    chartBucketWidthOf(span) shouldBe width
                }
            }
        }

        `when`("it's called with a span that has no round width of its own") {
            then("the width is rounded up to the next round one") {
                chartBucketWidthOf(Duration.ofSeconds(1_001)) shouldBe Duration.ofSeconds(2)
                chartBucketWidthOf(Duration.ofSeconds(60_001)) shouldBe Duration.ofMinutes(2)
            }
        }

        `when`("it's called with an empty or a huge span") {
            then("the width never gets below a second, and a very long span is split by whole days") {
                chartBucketWidthOf(Duration.ZERO) shouldBe Duration.ofSeconds(1)
                chartBucketWidthOf(Duration.ofDays(36_500)) shouldBe Duration.ofDays(37)
            }
        }

        `when`("it's called with any span") {
            then("the chart never gets more than about 1000 points") {
                listOf(1L, 59L, 3_599L, 86_399L, 604_801L, 2_592_000L, 31_536_000L, 3_153_600_000L)
                    .forEach { seconds ->
                        val points = seconds / chartBucketWidthOf(Duration.ofSeconds(seconds)).seconds
                        points shouldBeLessThanOrEqual 1_000L
                    }
            }
        }
    }
})
