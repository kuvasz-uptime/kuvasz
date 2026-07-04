package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.PendingFailureRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.events.PushMonitorDownEvent
import com.kuvaszuptime.kuvasz.models.events.PushMonitorUpEvent
import com.kuvaszuptime.kuvasz.models.events.PushUptimeMonitorEvent
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.testutils.forwardToSubscriber
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.shouldBeAfter
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.reactivex.rxjava3.subscribers.TestSubscriber
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.random.Random

@MicronautTest(startApplication = false)
class PushMonitorActionsTest(
    private val pushMonitorActions: PushMonitorActions,
    private val uptimeEventRepository: PushUptimeEventRepository,
    private val statCalculator: StatCalculator,
    private val pushMonitorRepository: PushMonitorRepository,
    private val eventDispatcher: EventDispatcher,
    private val pendingFailureRepository: PendingFailureRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getStatusPageDataOfEnabledMonitors() method") {

            fun randomUptimeEventCalculationContext() = UptimeEventCalculationContext(
                monitorId = Random.nextLong(),
                isMonitorEnabled = true,
                status = UptimeStatus.entries.toTypedArray().random(),
                startedAt = getCurrentTimestamp().minusDays((1..10).random().toLong()),
                endedAt = null as OffsetDateTime?,
                updatedAt = getCurrentTimestamp().minusDays((1..10).random().toLong()),
            )

            `when`("it is called without monitorIds") {

                val testPeriod = Duration.ofDays(7)
                val enabledMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                )

                val statCalculatorMock = getMock(statCalculator)
                every {
                    statCalculatorMock.calculateHistoricalPushUptimeStats(testPeriod, enabledMonitor.id)
                } returns HistoricalUptimeStatsDto(
                    period = "irrelevant",
                    incidents = 432,
                    affectedMonitors = 2343,
                    uptimeRatio = 0.2312,
                    totalDowntimeSeconds = 342342,
                )

                createPushMonitor(pushMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                createMaintenanceWindow(dslContext, global = true)
                val enabledMonitorsUptimeEvent = createPushUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )

                val uptimeEventRepoMock = getMock(uptimeEventRepository)
                val firstMonitorsUptimeCalcContexts = listOf(randomUptimeEventCalculationContext())
                every { uptimeEventRepoMock.fetchAllInPeriod(testPeriod, enabledMonitor.id) } returns
                    firstMonitorsUptimeCalcContexts
                every {
                    statCalculator.generateUptimeHistoryOverview(testPeriod, firstMonitorsUptimeCalcContexts)
                } returns listOf(StatusHistoryDto(LocalDate.now(), 12))

                // Executing the method under test
                val result = pushMonitorActions.getStatusPageDataOfEnabledMonitors(
                    period = Duration.ofDays(7),
                    monitorIds = null,
                )

                then("it should return all the enabled monitors") {

                    result shouldHaveSize 1
                    result.forOne { upMonitor ->
                        upMonitor.name shouldBe enabledMonitor.name
                        upMonitor.lastCheck shouldBe enabledMonitorsUptimeEvent.updatedAt
                        upMonitor.lastHeartbeat.shouldBeNull()
                        upMonitor.uptimeRatio shouldBe 0.2312
                        upMonitor.uptimeStatus shouldBe UptimeStatus.UP
                        upMonitor.uptimeStatusHistory shouldBe listOf(
                            StatusHistoryDto(LocalDate.now(), 12)
                        )
                        upMonitor.inMaintenance shouldBe true
                    }
                }
            }

            `when`("it is called with explicit monitorIds") {

                val testPeriod = Duration.ofDays(7)
                val lastHeartbeat = getCurrentTimestamp().minusDays(3)
                val enabledMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                    lastHeartbeat = lastHeartbeat,
                )
                val enabledMonitor2 = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor2",
                )

                val statCalculatorMock = getMock(statCalculator)
                every {
                    statCalculatorMock.calculateHistoricalPushUptimeStats(testPeriod, enabledMonitor.id)
                } returns HistoricalUptimeStatsDto(
                    period = "irrelevant",
                    incidents = 432,
                    affectedMonitors = 2343,
                    uptimeRatio = 0.2312,
                    totalDowntimeSeconds = 342342,
                )

                createPushMonitor(pushMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                val enabledMonitorsUptimeEvent = createPushUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                createPushUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(2),
                )

                val uptimeEventRepoMock = getMock(uptimeEventRepository)
                val firstMonitorsUptimeCalcContexts = listOf(randomUptimeEventCalculationContext())
                every { uptimeEventRepoMock.fetchAllInPeriod(testPeriod, enabledMonitor.id) } returns
                    firstMonitorsUptimeCalcContexts
                every {
                    statCalculator.generateUptimeHistoryOverview(testPeriod, firstMonitorsUptimeCalcContexts)
                } returns listOf(StatusHistoryDto(LocalDate.now(), 12))

                // Executing the method under test
                val result = pushMonitorActions.getStatusPageDataOfEnabledMonitors(
                    period = Duration.ofDays(7),
                    monitorIds = listOf(enabledMonitor.monitorId())
                )

                then("it should return only the requested monitor's data") {

                    result shouldHaveSize 1
                    result.forOne { upMonitor ->
                        upMonitor.name shouldBe enabledMonitor.name
                        upMonitor.lastCheck shouldBe enabledMonitorsUptimeEvent.updatedAt
                        upMonitor.lastHeartbeat shouldBe enabledMonitor.lastHeartbeat
                        upMonitor.uptimeRatio shouldBe 0.2312
                        upMonitor.uptimeStatus shouldBe UptimeStatus.UP
                        upMonitor.uptimeStatusHistory shouldBe listOf(
                            StatusHistoryDto(LocalDate.now(), 12)
                        )
                        upMonitor.inMaintenance shouldBe false
                    }
                }
            }
        }

        given("the updateLastHeartbeat() method") {

            `when`("it is called for a non existing monitor") {
                val testSecret = "secret1"
                val testTimestamp = getCurrentTimestamp()
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                pushMonitorActions.updateLastHeartbeat(testSecret, testTimestamp)

                then("it should not dispatch an uptime event") {

                    testSubscriber.assertNoValues()
                }
            }

            `when`("it is called for an existing, disabled monitor") {
                val testSecret = "secret1"
                val testTimestamp = getCurrentTimestamp()
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = false,
                    clientSecret = testSecret,
                )
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                pushMonitorActions.updateLastHeartbeat(testSecret, testTimestamp)

                then("it should not dispatch an uptime event but update the last heartbeat on the monitor") {
                    val monitorAfterUpdate = pushMonitorRepository.findById(testMonitor.id, null).shouldNotBeNull()

                    testSubscriber.assertNoValues()
                    monitorAfterUpdate.lastHeartbeat shouldBe testTimestamp
                    monitorAfterUpdate.updatedAt shouldBeAfter testMonitor.updatedAt
                }
            }

            `when`("it is called for an existing, enabled monitor") {
                val testSecret = "secret1"
                val testTimestamp = getCurrentTimestamp()
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    clientSecret = testSecret,
                )
                val eventRepoMock = getMock(uptimeEventRepository)
                val pendingFailureRepoMock = getMock(pendingFailureRepository)
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                val uptimeEventRecord = PushUptimeEventRecord().apply {
                    id = 3
                    monitorId = testMonitor.id
                    status = UptimeStatus.UP
                }
                every {
                    eventRepoMock.getPreviousEventByMonitorId(testMonitor.id, any())
                } returns uptimeEventRecord
                every { eventRepoMock.updateEvent(any(), any()) } returns 1
                every { pendingFailureRepoMock.deleteByMonitorId(testMonitor.id, any()) } returns 1

                pushMonitorActions.updateLastHeartbeat(testSecret, testTimestamp)

                then("it should dispatch an UP event") {

                    val dispatchedEvent = testSubscriber.awaitCount(1).values().first()
                    dispatchedEvent.shouldBeInstanceOf<PushMonitorUpEvent>()
                    dispatchedEvent.monitor.id shouldBe testMonitor.id
                    dispatchedEvent.monitor.lastHeartbeat shouldBe testTimestamp
                    dispatchedEvent.monitor.updatedAt shouldBeAfter testMonitor.updatedAt
                    dispatchedEvent.previousEvent shouldBe uptimeEventRecord
                }
            }
        }

        given("the signalFailure() method") {

            `when`("it is called for a non existing monitor") {
                val testSecret = "secret1"
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                pushMonitorActions.signalFailure(testSecret, "some nice error")

                then("it should not dispatch an uptime event") {

                    testSubscriber.assertNoValues()
                }
            }

            `when`("it is called for an existing, disabled monitor") {
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = false,
                    clientSecret = "secret1",
                )
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                pushMonitorActions.signalFailure(testMonitor.clientSecret, "other nice error")

                then("it should not dispatch an uptime event") {
                    testSubscriber.assertNoValues()
                }
            }

            `when`("it is called for an existing, enabled monitor without a previous event") {
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    clientSecret = "secret1",
                )
                val eventRepoMock = getMock(uptimeEventRepository)
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                val uptimeEventRecord = PushUptimeEventRecord().apply {
                    id = 3
                    monitorId = testMonitor.id
                    status = UptimeStatus.DOWN
                }
                every {
                    eventRepoMock.getPreviousEventByMonitorId(testMonitor.id, any())
                } returns null
                every { eventRepoMock.insertFromMonitorEvent(any(), any()) } returns uptimeEventRecord

                pushMonitorActions.signalFailure(testMonitor.clientSecret, "oh my gosh")

                then("it should dispatch a DOWN event") {

                    val dispatchedEvent = testSubscriber.awaitCount(1).values().first()
                    dispatchedEvent.shouldBeInstanceOf<PushMonitorDownEvent>()
                    dispatchedEvent.monitor.id shouldBe testMonitor.id
                    dispatchedEvent.error shouldBe "oh my gosh"
                    dispatchedEvent.isManual shouldBe true
                    dispatchedEvent.previousEvent shouldBe null
                }
            }

            `when`("it's called for an existing, enabled monitor w/o a previous failure - failure threshold is 2") {
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    clientSecret = "secret1",
                    failureCountThreshold = 2,
                )
                val eventRepoMock = getMock(uptimeEventRepository)
                val pendingFailureRepoMock = getMock(pendingFailureRepository)
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                val uptimeEventRecord = PushUptimeEventRecord().apply {
                    id = 3
                    monitorId = testMonitor.id
                    status = UptimeStatus.DOWN
                }
                every {
                    eventRepoMock.getPreviousEventByMonitorId(testMonitor.id, any())
                } returns null
                every { eventRepoMock.insertFromMonitorEvent(any(), any()) } returns uptimeEventRecord
                every { pendingFailureRepoMock.createOrIncrement(testMonitor.id) } returns
                    PendingFailureRecord().apply {
                        monitorId = testMonitor.id
                        failureCount = 1
                    }

                pushMonitorActions.signalFailure(testMonitor.clientSecret, "oh my gosh")

                then("it should NOT dispatch a DOWN event") {

                    testSubscriber.assertNoValues()
                }
            }

            `when`("it is called for an existing, enabled monitor with a previous event") {
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    clientSecret = "secret1",
                )
                val eventRepoMock = getMock(uptimeEventRepository)
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                val uptimeEventRecord = PushUptimeEventRecord().apply {
                    id = 3
                    monitorId = testMonitor.id
                    status = UptimeStatus.DOWN
                }
                every {
                    eventRepoMock.getPreviousEventByMonitorId(testMonitor.id, any())
                } returns uptimeEventRecord
                every { eventRepoMock.updateEvent(any(), any()) } returns 1

                pushMonitorActions.signalFailure(testMonitor.clientSecret, "oh my gosh")

                then("it should dispatch a DOWN event") {

                    val dispatchedEvent = testSubscriber.awaitCount(1).values().first()
                    dispatchedEvent.shouldBeInstanceOf<PushMonitorDownEvent>()
                    dispatchedEvent.monitor.id shouldBe testMonitor.id
                    dispatchedEvent.error shouldBe "oh my gosh"
                    dispatchedEvent.isManual shouldBe true
                    dispatchedEvent.previousEvent shouldBe uptimeEventRecord
                }
            }

            `when`("it's called for an existing, enabled monitor w/ a previous failure - failure threshold is 3") {
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    clientSecret = "secret1",
                    failureCountThreshold = 3,
                )
                val eventRepoMock = getMock(uptimeEventRepository)
                val pendingFailureRepoMock = getMock(pendingFailureRepository)
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                val uptimeEventRecord = PushUptimeEventRecord().apply {
                    id = 3
                    monitorId = testMonitor.id
                    status = UptimeStatus.UP
                }
                every {
                    eventRepoMock.getPreviousEventByMonitorId(testMonitor.id, any())
                } returns uptimeEventRecord
                every { eventRepoMock.updateEvent(any(), any()) } returns 1
                every { pendingFailureRepoMock.createOrIncrement(testMonitor.id) } returns
                    PendingFailureRecord().apply {
                        monitorId = testMonitor.id
                        failureCount = 2
                    }

                pushMonitorActions.signalFailure(testMonitor.clientSecret, "oh my gosh")

                then("it should NOT dispatch a DOWN event") {

                    testSubscriber.assertNoValues()
                }
            }

            `when`("it's called for an existing, enabled monitor w/ a previous event - higher threshold reached") {
                val testMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    clientSecret = "secret1",
                    failureCountThreshold = 2
                )
                val eventRepoMock = getMock(uptimeEventRepository)
                val pendingFailureRepoMock = getMock(pendingFailureRepository)
                val testSubscriber = TestSubscriber<PushUptimeMonitorEvent>()
                eventDispatcher.subscribeToPushMonitorEvents { it.forwardToSubscriber(testSubscriber) }

                val uptimeEventRecord = PushUptimeEventRecord().apply {
                    id = 3
                    monitorId = testMonitor.id
                    status = UptimeStatus.UP
                }
                every {
                    eventRepoMock.getPreviousEventByMonitorId(testMonitor.id, any())
                } returns uptimeEventRecord
                every { eventRepoMock.updateEvent(any(), any()) } returns 1
                every { eventRepoMock.endEventById(any(), any(), any()) } returns 1
                every { eventRepoMock.insertFromMonitorEvent(any(), any()) } returns mockk()
                every { pendingFailureRepoMock.createOrIncrement(testMonitor.id) } returns
                    PendingFailureRecord().apply {
                        monitorId = testMonitor.id
                        failureCount = 2
                    }
                every { pendingFailureRepoMock.deleteByMonitorId(testMonitor.id, any()) } returns 1

                pushMonitorActions.signalFailure(testMonitor.clientSecret, "oh my gosh")

                then("it should dispatch a DOWN event") {

                    val dispatchedEvent = testSubscriber.awaitCount(1).values().first()
                    dispatchedEvent.shouldBeInstanceOf<PushMonitorDownEvent>()
                    dispatchedEvent.monitor.id shouldBe testMonitor.id
                    dispatchedEvent.error shouldBe "oh my gosh"
                    dispatchedEvent.isManual shouldBe true
                    dispatchedEvent.previousEvent shouldBe uptimeEventRecord
                }
            }
        }
    }

    @MockBean(PushUptimeEventRepository::class)
    fun pushUptimeEventRepository(): PushUptimeEventRepository = mockk()

    @MockBean(PendingFailureRepository::class)
    fun pendingFailureRepository(): PendingFailureRepository = mockk()

    @MockBean(StatCalculator::class)
    fun statCalculator(): StatCalculator = mockk()
}
