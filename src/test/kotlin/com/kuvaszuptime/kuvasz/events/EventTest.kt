package com.kuvaszuptime.kuvasz.events

import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.tables.records.UptimeEventRecord
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.micronaut.http.HttpStatus
import io.mockk.mockk
import java.time.OffsetDateTime

class EventTest : BehaviorSpec() {
    init {
        given("UptimeMonitorEvent.getEndedEventDuration() method") {
            `when`("the receiver's state differs from previousEvent's") {
                val previousEvent = UptimeEventRecord()
                    .setStatus(UptimeStatus.DOWN)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = previousEvent
                )
                then("it should return a Duration") {
                    event.getEndedEventDuration() shouldNotBe null
                }
            }

            `when`("the receiver's state is the same as previousEvent's") {
                val previousEvent = UptimeEventRecord()
                    .setStatus(UptimeStatus.UP)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = previousEvent
                )
                then("it should return null") {
                    event.getEndedEventDuration() shouldBe null
                }
            }

            `when`("previousEvent is null") {
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                then("it should return null") {
                    event.getEndedEventDuration() shouldBe null
                }
            }
        }

        given("UptimeMonitorEvent.continueWhenStateChanges() method") {
            `when`("the receiver's state differs from previousEvent's") {
                val previousEvent = UptimeEventRecord()
                    .setStatus(UptimeStatus.DOWN)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = previousEvent
                )
                var testValue = 0

                then("it should invoke the given lambda") {
                    event.runWhenStateChanges {
                        testValue = 1
                    }
                    testValue shouldBe 1
                }
            }

            `when`("the receiver's state is the same as previousEvent's") {
                val previousEvent = UptimeEventRecord()
                    .setStatus(UptimeStatus.UP)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = previousEvent
                )
                var testValue = 0

                then("it should not invoke the given lambda") {
                    event.runWhenStateChanges {
                        testValue = 1
                    }
                    testValue shouldBe 0
                }
            }

            `when`("previousEvent is null") {
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = null
                )
                var testValue = 0

                then("it should invoke the given lambda") {
                    event.runWhenStateChanges {
                        testValue = 1
                    }
                    testValue shouldBe 1
                }
            }
        }
    }
}
