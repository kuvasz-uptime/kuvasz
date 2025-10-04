package com.kuvaszuptime.kuvasz.services.check.push

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.stats.HistoricalUptimeStatsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushUptimeEventRepository
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.UptimeEventCalculationContext
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.random.Random

@MicronautTest
class PushMonitorActionsTest(
    private val pushMonitorActions: PushMonitorActions,
    private val uptimeEventRepository: PushUptimeEventRepository,
    private val statCalculator: StatCalculator,
    private val pushMonitorRepository: PushMonitorRepository,
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
                        upMonitor.averageLatencyInMs.shouldBeNull()
                        upMonitor.uptimeRatio shouldBe 0.2312
                        upMonitor.uptimeStatus shouldBe UptimeStatus.UP
                        upMonitor.uptimeStatusHistory shouldBe listOf(
                            StatusHistoryDto(LocalDate.now(), 12)
                        )
                    }
                }
            }

            `when`("it is called with explicit monitorIds") {

                val testPeriod = Duration.ofDays(7)
                val enabledMonitor = createPushMonitor(
                    pushMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
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
                        upMonitor.averageLatencyInMs.shouldBeNull()
                        upMonitor.uptimeRatio shouldBe 0.2312
                        upMonitor.uptimeStatus shouldBe UptimeStatus.UP
                        upMonitor.uptimeStatusHistory shouldBe listOf(
                            StatusHistoryDto(LocalDate.now(), 12)
                        )
                    }
                }
            }
        }
    }

    @MockBean(PushUptimeEventRepository::class)
    fun pushUptimeEventRepository(): PushUptimeEventRepository = mockk()

    @MockBean(StatCalculator::class)
    fun statCalculator(): StatCalculator = mockk()
}
