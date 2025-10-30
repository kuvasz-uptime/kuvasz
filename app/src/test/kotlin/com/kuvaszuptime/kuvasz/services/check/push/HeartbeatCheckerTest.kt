package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.handlers.DatabaseEventHandler
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
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
    val heartbeatChecker = HeartbeatChecker(
        dslCtx = dslContext,
        eventDispatcher = dispatcher,
        pushMonitorRepository = monitorRepoMock,
        uptimeEventRepository = uptimeEventRepoMock,
        databaseEventHandler = mockDbEventHandler,
    )

    given("the HeartbeatChecker logic") {

        `when`("the checker is called") {

            val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
            dispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }
            val mockMonitorList = listOf(
                PushMonitorRecord().apply { id = 1 },
                PushMonitorRecord().apply { id = 2 },
            )
            val mockUptimeEventRecord = PushUptimeEventRecord().apply { id = 12 }

            every { monitorRepoMock.fetchWithMissedHeartbeats(any()) } returns mockMonitorList
            every { uptimeEventRepoMock.getPreviousEventByMonitorId(1, any()) } returns mockUptimeEventRecord
            every { uptimeEventRepoMock.getPreviousEventByMonitorId(2, any()) } returns null

            heartbeatChecker.checkHeartbeats()

            then("it should dispatch a down event for every monitor that has a missed heartbeat") {

                val events = testSubscriber.awaitCount(2).values()
                events.forAll { event ->
                    event.shouldBeInstanceOf<PushMonitorDownEvent>()
                    event.error shouldBe Messages.missedHeartbeat()
                    event.isManual shouldBe false
                }
                events.forOne { firstEvent ->
                    firstEvent.monitor shouldBe mockMonitorList[0]
                    firstEvent.previousEvent shouldBe mockUptimeEventRecord
                }

                events.forOne { firstEvent ->
                    firstEvent.monitor shouldBe mockMonitorList[1]
                    firstEvent.previousEvent shouldBe null
                }

                verify(exactly = 2) { mockDbEventHandler.handleUptimeMonitorEvent(any<PushMonitorDownEvent>()) }
            }
        }
    }
})
