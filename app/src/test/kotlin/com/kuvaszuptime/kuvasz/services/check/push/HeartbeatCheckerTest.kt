package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.PendingFailureRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import io.kotest.inspectors.forAll
import io.kotest.inspectors.forOne
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.subscribers.TestSubscriber
import org.jooq.DSLContext

@MicronautTest(startApplication = false)
class HeartbeatCheckerTest(
    dslContext: DSLContext,
) : DatabaseBehaviorSpec({

    val dispatcher = EventDispatcher()
    val monitorRepoMock = mockk<PushMonitorRepository>()
    val uptimeEventRepoMock = mockk<PushUptimeEventRepository>()
    val mockDbEventHandler = mockk<DatabaseEventHandler>(relaxed = true)
    val mockPendingFailureRepo = mockk<PendingFailureRepository>()
    val heartbeatChecker = HeartbeatChecker(
        dslCtx = dslContext,
        eventDispatcher = dispatcher,
        pushMonitorRepository = monitorRepoMock,
        uptimeEventRepository = uptimeEventRepoMock,
        databaseEventHandler = mockDbEventHandler,
        pendingFailureRepository = mockPendingFailureRepo,
    )

    given("the HeartbeatChecker logic") {

        `when`("the checker is called") {

            fun mockPendingFailure(monitorId: Long, failureCount: Long) {
                every { mockPendingFailureRepo.createOrIncrement(monitorId) } returns PendingFailureRecord().apply {
                    this.monitorId = monitorId
                    this.failureCount = failureCount
                }
            }

            val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
            dispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }
            val mockMonitorList = listOf(
                PushMonitorRecord().apply {
                    id = 1
                    failureCountThreshold = 1
                },
                PushMonitorRecord().apply {
                    id = 2
                    failureCountThreshold = 1
                },
                PushMonitorRecord().apply {
                    id = 3
                    failureCountThreshold = 2
                },
                PushMonitorRecord().apply {
                    id = 4
                    failureCountThreshold = 2
                },
            )
            val mockUptimeEventRecord = PushUptimeEventRecord().apply { id = 12 }

            every { monitorRepoMock.fetchWithMissedHeartbeats(any()) } returns mockMonitorList

            every { uptimeEventRepoMock.getPreviousEventByMonitorId(1, any()) } returns mockUptimeEventRecord

            every { uptimeEventRepoMock.getPreviousEventByMonitorId(2, any()) } returns null

            every { uptimeEventRepoMock.getPreviousEventByMonitorId(3, any()) } returns mockUptimeEventRecord
            mockPendingFailure(3, 2)
            every { mockPendingFailureRepo.deleteByMonitorId(3, any()) } returns 1

            every { uptimeEventRepoMock.getPreviousEventByMonitorId(4, any()) } returns null
            mockPendingFailure(4, 1)

            heartbeatChecker.checkHeartbeats()

            then("it should dispatch a down event for every monitor that has a missed heartbeat") {

                val events = testSubscriber.awaitCount(3).values()
                events.forAll { event ->
                    event.shouldBeInstanceOf<PushMonitorDownEvent>()
                    event.error shouldBe Messages.missedHeartbeat()
                    event.isManual shouldBe false
                }
                events.forOne { firstEvent ->
                    firstEvent.monitor shouldBe mockMonitorList[0]
                    firstEvent.previousEvent shouldBe mockUptimeEventRecord
                }

                events.forOne { secondEvent ->
                    secondEvent.monitor shouldBe mockMonitorList[1]
                    secondEvent.previousEvent shouldBe null
                }

                events.forOne { thirdEvent ->
                    thirdEvent.monitor shouldBe mockMonitorList[2]
                    thirdEvent.previousEvent shouldBe mockUptimeEventRecord
                }

                verify(exactly = 3) { mockDbEventHandler.handleUptimeMonitorEvent(any<PushMonitorDownEvent>()) }
            }
        }
    }
})
