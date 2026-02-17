package com.kuvaszuptime.kuvasz.services.check

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.PendingFailureRecord
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.services.check.http.mockMonitor
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.mockk.every
import io.mockk.mockk

class FailureThresholdHandlingTest : BehaviorSpec({

    val mockFailureRepo = mockk<PendingFailureRepository>()

    given("UptimeMonitorEvent.isDownNow()") {

        `when`("the event is UP") {

            then("it should return false") {

                HttpMonitorUpEvent(
                    monitor = mockMonitor(failureCountThreshold = 3),
                    status = HttpStatus.OK,
                    latency = 100,
                    previousEvent = null
                ).isDownNow(mockFailureRepo) shouldBe false
            }
        }

        `when`("the previous event was DOWN, threshold > 1") {

            then("it should return true w/o checking the pending failures") {

                HttpMonitorDownEvent(
                    monitor = mockMonitor(failureCountThreshold = 3),
                    status = HttpStatus.OK,
                    error = Exception(),
                    previousEvent = mockk { every { status } returns UptimeStatus.DOWN }
                ).isDownNow(mockFailureRepo) shouldBe true
            }
        }

        `when`("the previous event was UP, threshold < 2") {

            then("it should return true w/o checking the pending failures") {

                HttpMonitorDownEvent(
                    monitor = mockMonitor(failureCountThreshold = 1),
                    status = HttpStatus.OK,
                    error = Exception(),
                    previousEvent = mockk { every { status } returns UptimeStatus.UP }
                ).isDownNow(mockFailureRepo) shouldBe true
            }
        }

        `when`("the previous event was null, threshold < 2") {

            then("it should return true w/o checking the pending failures") {

                HttpMonitorDownEvent(
                    monitor = mockMonitor(failureCountThreshold = 1),
                    status = HttpStatus.OK,
                    error = Exception(),
                    previousEvent = null
                ).isDownNow(mockFailureRepo) shouldBe true
            }
        }

        `when`("the previous event was null, threshold > 1, threshold is not reached") {

            then("it should return false after checking the pending failures") {
                every { mockFailureRepo.createOrIncrement(any()) } returns
                    PendingFailureRecord().apply {
                        this.monitorId = 1
                        this.failureCount = 1
                    }

                HttpMonitorDownEvent(
                    monitor = mockMonitor(failureCountThreshold = 2),
                    status = HttpStatus.OK,
                    error = Exception(),
                    previousEvent = null
                ).isDownNow(mockFailureRepo) shouldBe false
            }
        }

        `when`("the previous event was UP, threshold > 1, threshold is not reached") {

            then("it should return false after checking the pending failures") {
                every { mockFailureRepo.createOrIncrement(any()) } returns
                    PendingFailureRecord().apply {
                        this.monitorId = 1
                        this.failureCount = 1
                    }

                HttpMonitorDownEvent(
                    monitor = mockMonitor(failureCountThreshold = 2),
                    status = HttpStatus.OK,
                    error = Exception(),
                    previousEvent = mockk { every { status } returns UptimeStatus.UP }
                ).isDownNow(mockFailureRepo) shouldBe false
            }
        }

        `when`("the previous event was null, threshold > 1, threshold is reached") {

            then("it should return false after checking the pending failures") {
                every { mockFailureRepo.createOrIncrement(any()) } returns
                    PendingFailureRecord().apply {
                        this.monitorId = 1
                        this.failureCount = 2
                    }
                every { mockFailureRepo.deleteByMonitorId(1, any()) } returns 1

                HttpMonitorDownEvent(
                    monitor = mockMonitor(failureCountThreshold = 2),
                    status = HttpStatus.OK,
                    error = Exception(),
                    previousEvent = null
                ).isDownNow(mockFailureRepo) shouldBe true
            }
        }

        `when`("the previous event was UP, threshold > 1, threshold is reached") {

            then("it should return false after checking the pending failures") {
                every { mockFailureRepo.createOrIncrement(any()) } returns
                    PendingFailureRecord().apply {
                        this.monitorId = 1
                        this.failureCount = 2
                    }
                every { mockFailureRepo.deleteByMonitorId(1, any()) } returns 1

                HttpMonitorDownEvent(
                    monitor = mockMonitor(failureCountThreshold = 2),
                    status = HttpStatus.OK,
                    error = Exception(),
                    previousEvent = mockk { every { status } returns UptimeStatus.UP }
                ).isDownNow(mockFailureRepo) shouldBe true
            }
        }
    }
})
