package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.LatencyMetricResult
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.UptimeOverview
import com.kuvaszuptime.kuvasz.testutils.shouldBe
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import java.time.LocalDate

@MicronautTest
class HttpMonitorActionsTest(
    private val httpMonitorActions: HttpMonitorActions,
    private val statCalculator: StatCalculator,
    private val latencyLogRepository: HttpLatencyLogRepository,
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getStatusPageDataOfEnabledMonitors() method") {

            `when`("it is called without monitorIds") {

                val testPeriod = Duration.ofDays(7)
                val enabledMonitor = createHttpMonitor(
                    httpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                    latencyHistoryEnabled = true,
                )
                val enabledMonitor2 = createHttpMonitor(
                    httpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor2",
                    latencyHistoryEnabled = false,
                )

                val statCalculatorMock = getMock(statCalculator)
                val latencyLogRepositoryMock = getMock(latencyLogRepository)
                every {
                    latencyLogRepositoryMock.getLatencyMetrics(enabledMonitor.id, testPeriod)
                } returns LatencyMetricResult(
                    monitorId = enabledMonitor.id,
                    avg = 123,
                    min = 6982,
                    max = 2814,
                    p90 = 9114,
                    p95 = 8989,
                    p99 = 3129,
                )

                createHttpMonitor(httpMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                createMaintenanceWindow(dslContext, global = true)
                val enabledMonitorsUptimeEvent = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                val enabledMonitorsUptimeEvent2 = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(2),
                )

                every {
                    statCalculatorMock.calculateUptimeOverviews(
                        monitorType = MonitorType.HTTP_SSL,
                        period = testPeriod,
                        monitorIds = match { it.toSet() == setOf(enabledMonitor.id, enabledMonitor2.id) },
                    )
                } returns mapOf(
                    enabledMonitor.id to UptimeOverview(
                        uptimeRatio = 0.2312,
                        statusHistory = listOf(StatusHistoryDto(LocalDate.now(), 12)),
                    ),
                    enabledMonitor2.id to UptimeOverview(
                        uptimeRatio = 0.0123,
                        statusHistory = listOf(StatusHistoryDto(LocalDate.now(), 34)),
                    ),
                )

                // Executing the method under test
                val result = httpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    period = Duration.ofDays(7),
                    monitorIds = null,
                )

                then("it should return all the enabled monitors") {

                    result shouldHaveSize 2
                    result.forOne { upMonitor ->
                        upMonitor.name shouldBe enabledMonitor.name
                        upMonitor.lastCheck shouldBe enabledMonitorsUptimeEvent.updatedAt
                        upMonitor.averageLatencyInMs shouldBe 123
                        upMonitor.uptimeRatio shouldBe 0.2312
                        upMonitor.uptimeStatus shouldBe UptimeStatus.UP
                        upMonitor.uptimeStatusHistory shouldBe listOf(
                            StatusHistoryDto(LocalDate.now(), 12)
                        )
                        upMonitor.inMaintenance shouldBe true
                    }
                    result.forOne { downMonitor ->
                        downMonitor.name shouldBe enabledMonitor2.name
                        downMonitor.lastCheck shouldBe enabledMonitorsUptimeEvent2.updatedAt
                        downMonitor.averageLatencyInMs shouldBe null
                        downMonitor.uptimeRatio shouldBe 0.0123
                        downMonitor.uptimeStatus shouldBe UptimeStatus.DOWN
                        downMonitor.uptimeStatusHistory shouldBe listOf(
                            StatusHistoryDto(LocalDate.now(), 34)
                        )
                        downMonitor.inMaintenance shouldBe true
                    }
                }
            }

            `when`("it is called with explicit monitorIds") {

                val testPeriod = Duration.ofDays(7)
                val enabledMonitor = createHttpMonitor(
                    httpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                    latencyHistoryEnabled = true,
                )
                val enabledMonitor2 = createHttpMonitor(
                    httpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor2",
                    latencyHistoryEnabled = false,
                )

                val statCalculatorMock = getMock(statCalculator)
                val latencyLogRepositoryMock = getMock(latencyLogRepository)
                every {
                    latencyLogRepositoryMock.getLatencyMetrics(enabledMonitor.id, testPeriod)
                } returns LatencyMetricResult(
                    monitorId = enabledMonitor.id,
                    avg = 123,
                    min = 6982,
                    max = 2814,
                    p90 = 9114,
                    p95 = 8989,
                    p99 = 3129,
                )

                createHttpMonitor(httpMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                val enabledMonitorsUptimeEvent = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(2),
                )

                every {
                    statCalculatorMock.calculateUptimeOverviews(
                        monitorType = MonitorType.HTTP_SSL,
                        period = testPeriod,
                        monitorIds = listOf(enabledMonitor.id),
                    )
                } returns mapOf(
                    enabledMonitor.id to UptimeOverview(
                        uptimeRatio = 0.2312,
                        statusHistory = listOf(StatusHistoryDto(LocalDate.now(), 12)),
                    ),
                )

                // Executing the method under test
                val result = httpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    period = Duration.ofDays(7),
                    monitorIds = listOf(enabledMonitor.monitorId())
                )

                then("it should return only the requested monitor's data") {

                    result shouldHaveSize 1
                    result.forOne { upMonitor ->
                        upMonitor.name shouldBe enabledMonitor.name
                        upMonitor.lastCheck shouldBe enabledMonitorsUptimeEvent.updatedAt
                        upMonitor.averageLatencyInMs shouldBe 123
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
    }

    @MockBean(StatCalculator::class)
    fun statCalculator(): StatCalculator = mockk()

    @MockBean(HttpLatencyLogRepository::class)
    fun httpLatencyLogRepository(): HttpLatencyLogRepository = mockk()
}
