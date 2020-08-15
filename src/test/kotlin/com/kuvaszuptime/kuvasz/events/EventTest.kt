package com.kuvaszuptime.kuvasz.events

import arrow.core.Option
import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.MonitorUpEvent
import com.kuvaszuptime.kuvasz.models.getEndedEventDuration
import com.kuvaszuptime.kuvasz.models.runWhenStateChanges
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.mockk.mockk
import java.time.OffsetDateTime

class EventTest : BehaviorSpec() {
    init {
        given("UptimeMonitorEvent.getEndedEventDuration() method") {
            `when`("the receiver's state differs from previousEvent's") {
                val previousEvent = UptimeEventPojo()
                    .setStatus(UptimeStatus.DOWN)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(previousEvent)
                )
                then("it should return Some") {
                    val result = event.getEndedEventDuration()
                    result.isDefined() shouldBe true
                }
            }

            `when`("the receiver's state is the same as previousEvent's") {
                val previousEvent = UptimeEventPojo()
                    .setStatus(UptimeStatus.UP)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(previousEvent)
                )
                then("it should return Empty") {
                    val result = event.getEndedEventDuration()
                    result.isEmpty() shouldBe true
                }
            }

            `when`("previousEvent is an Empty") {
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
                )
                then("it should return Empty") {
                    val result = event.getEndedEventDuration()
                    result.isEmpty() shouldBe true
                }
            }
        }

        given("UptimeMonitorEvent.continueWhenStateChanges() method") {
            `when`("the receiver's state differs from previousEvent's") {
                val previousEvent = UptimeEventPojo()
                    .setStatus(UptimeStatus.DOWN)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(previousEvent)
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
                val previousEvent = UptimeEventPojo()
                    .setStatus(UptimeStatus.UP)
                    .setStartedAt(OffsetDateTime.now())
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.just(previousEvent)
                )
                var testValue = 0

                then("it should not invoke the given lambda") {
                    event.runWhenStateChanges {
                        testValue = 1
                    }
                    testValue shouldBe 0
                }
            }

            `when`("previousEvent is an Empty") {
                val event = MonitorUpEvent(
                    monitor = mockk(),
                    status = HttpStatus.OK,
                    latency = 1000,
                    previousEvent = Option.empty()
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
