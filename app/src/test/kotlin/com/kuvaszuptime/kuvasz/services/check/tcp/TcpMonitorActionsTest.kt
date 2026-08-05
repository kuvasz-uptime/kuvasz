package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.monitor.tcp.monitorId
import com.kuvaszuptime.kuvasz.repositories.LatencyMetricResult
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.UptimeOverview
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
class TcpMonitorActionsTest(
    private val tcpMonitorActions: TcpMonitorActions,
    private val statCalculator: StatCalculator,
    private val metricsLogRepository: TcpMetricsLogRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getStatusPageDataOfEnabledMonitors() method") {

            `when`("it is called without monitorIds") {

                val testPeriod = Duration.ofDays(7)
                val enabledMonitor = createTcpMonitor(
                    tcpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                    metricsHistoryEnabled = true,
                )
                val enabledMonitor2 = createTcpMonitor(
                    tcpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor2",
                    metricsHistoryEnabled = false,
                )

                val statCalculatorMock = getMock(statCalculator)
                val metricsLogRepositoryMock = getMock(metricsLogRepository)
                every {
                    metricsLogRepositoryMock.getLatencyMetrics(enabledMonitor.id, testPeriod)
                } returns LatencyMetricResult(
                    monitorId = enabledMonitor.id,
                    avg = 123,
                    min = 6982,
                    max = 2814,
                    p90 = 9114,
                    p95 = 8989,
                    p99 = 3129,
                )

                createTcpMonitor(tcpMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                createMaintenanceWindow(dslContext, global = true)
                val enabledMonitorsUptimeEvent = createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                val enabledMonitorsUptimeEvent2 = createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(2),
                )

                every {
                    statCalculatorMock.calculateUptimeOverview(MonitorType.TCP, testPeriod, enabledMonitor.id)
                } returns UptimeOverview(
                    uptimeRatio = 0.2312,
                    statusHistory = listOf(StatusHistoryDto(LocalDate.now(), 12)),
                )
                every {
                    statCalculatorMock.calculateUptimeOverview(MonitorType.TCP, testPeriod, enabledMonitor2.id)
                } returns UptimeOverview(
                    uptimeRatio = 0.0123,
                    statusHistory = listOf(StatusHistoryDto(LocalDate.now(), 34)),
                )

                // Executing the method under test
                val result = tcpMonitorActions.getStatusPageDataOfEnabledMonitors(
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
                val enabledMonitor = createTcpMonitor(
                    tcpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                    metricsHistoryEnabled = true,
                )
                val enabledMonitor2 = createTcpMonitor(
                    tcpMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor2",
                    metricsHistoryEnabled = false,
                )

                val statCalculatorMock = getMock(statCalculator)
                val metricsLogRepositoryMock = getMock(metricsLogRepository)
                every {
                    metricsLogRepositoryMock.getLatencyMetrics(enabledMonitor.id, testPeriod)
                } returns LatencyMetricResult(
                    monitorId = enabledMonitor.id,
                    avg = 123,
                    min = 6982,
                    max = 2814,
                    p90 = 9114,
                    p95 = 8989,
                    p99 = 3129,
                )

                createTcpMonitor(tcpMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                val enabledMonitorsUptimeEvent = createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                createTcpUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(2),
                )

                every {
                    statCalculatorMock.calculateUptimeOverview(MonitorType.TCP, testPeriod, enabledMonitor.id)
                } returns UptimeOverview(
                    uptimeRatio = 0.2312,
                    statusHistory = listOf(StatusHistoryDto(LocalDate.now(), 12)),
                )

                // Executing the method under test
                val result = tcpMonitorActions.getStatusPageDataOfEnabledMonitors(
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

    @MockBean(TcpMetricsLogRepository::class)
    fun tcpMetricsLogRepository(): TcpMetricsLogRepository = mockk()
}
