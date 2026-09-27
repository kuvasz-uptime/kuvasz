package com.kuvaszuptime.kuvasz.services.check.docker

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.monitor.docker.monitorId
import com.kuvaszuptime.kuvasz.repositories.LatencyMetricResult
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
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
class DockerMonitorActionsTest(
    private val dockerMonitorActions: DockerMonitorActions,
    private val statCalculator: StatCalculator,
    private val metricsLogRepository: DockerMetricsLogRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        given("the getStatusPageDataOfEnabledMonitors() method") {

            `when`("it is called without monitorIds") {

                val testPeriod = Duration.ofDays(7)
                val enabledMonitor = createDockerMonitor(
                    dockerMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                    metricsHistoryEnabled = true,
                )
                val enabledMonitor2 = createDockerMonitor(
                    dockerMonitorRepository,
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

                createDockerMonitor(dockerMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                createMaintenanceWindow(dslContext, global = true)
                val enabledMonitorsUptimeEvent = createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                val enabledMonitorsUptimeEvent2 = createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(2),
                )

                every {
                    statCalculatorMock.calculateUptimeOverviews(
                        monitorType = MonitorType.DOCKER,
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
                val result = dockerMonitorActions.getStatusPageDataOfEnabledMonitors(
                    period = Duration.ofDays(7),
                    monitorIds = null,
                    categories = null,
                )

                then("it should return all the enabled monitors") {

                    result shouldHaveSize 2
                    result.forOne { upMonitor ->
                        upMonitor.name shouldBe enabledMonitor.name
                        upMonitor.lastCheck shouldBe enabledMonitorsUptimeEvent.updatedAt
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
                val enabledMonitor = createDockerMonitor(
                    dockerMonitorRepository,
                    enabled = true,
                    monitorName = "enabled-monitor",
                    metricsHistoryEnabled = true,
                )
                val enabledMonitor2 = createDockerMonitor(
                    dockerMonitorRepository,
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

                createDockerMonitor(dockerMonitorRepository, enabled = false, monitorName = "disabled-monitor")
                val enabledMonitorsUptimeEvent = createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor.id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp().minusDays(3),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(3),
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = enabledMonitor2.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp().minusDays(2),
                    endedAt = null,
                    updatedAt = getCurrentTimestamp().minusDays(2),
                )

                every {
                    statCalculatorMock.calculateUptimeOverviews(
                        monitorType = MonitorType.DOCKER,
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
                val result = dockerMonitorActions.getStatusPageDataOfEnabledMonitors(
                    period = Duration.ofDays(7),
                    monitorIds = listOf(enabledMonitor.monitorId()),
                    categories = null,
                )

                then("it should return only the requested monitor's data") {

                    result shouldHaveSize 1
                    result.forOne { upMonitor ->
                        upMonitor.name shouldBe enabledMonitor.name
                        upMonitor.lastCheck shouldBe enabledMonitorsUptimeEvent.updatedAt
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

    @MockBean(DockerMetricsLogRepository::class)
    fun dockerMetricsLogRepository(): DockerMetricsLogRepository = mockk()
}
