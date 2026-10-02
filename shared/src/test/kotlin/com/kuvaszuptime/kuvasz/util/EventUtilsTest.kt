package com.kuvaszuptime.kuvasz.util

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

class EventUtilsTest : BehaviorSpec() {
    init {
        val now = OffsetDateTime.of(2026, 10, 1, 12, 0, 0, 0, ZoneOffset.UTC)
        val startedAt = now.minusHours(3)
        val updatedAt = now.minusHours(1)
        val endedAt = now.minusHours(2)

        given("getEffectiveEndOfEvent() and getDurationOfEvent()") {
            listOf(
                Triple("the event has ended, while the monitor is enabled", true, endedAt) to endedAt,
                Triple("the event has ended, while the monitor is paused", false, endedAt) to endedAt,
                Triple("the event is ongoing, while the monitor is enabled", true, null) to now,
                // The last update is the last known date when the state was effective
                Triple("the event is ongoing, while the monitor is paused", false, null) to updatedAt,
            ).forEach { (scenario, expectedEnd) ->
                val (description, isMonitorEnabled, eventEndedAt) = scenario
                `when`(description) {

                    then("the event should be effective until $expectedEnd") {
                        getEffectiveEndOfEvent(isMonitorEnabled, eventEndedAt, updatedAt, now) shouldBe expectedEnd
                        getDurationOfEvent(isMonitorEnabled, startedAt, eventEndedAt, updatedAt, now) shouldBe
                            startedAt.until(expectedEnd, ChronoUnit.SECONDS)
                    }
                }
            }
        }
    }
}
