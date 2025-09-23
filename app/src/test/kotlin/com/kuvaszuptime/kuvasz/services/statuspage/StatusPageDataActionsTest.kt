package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.StatusPageNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import java.time.Duration

@MicronautTest(environments = ["full-default-status-page-config"])
class StatusPageDataActionsTest(
    private val statusPageActions: StatusPageDataActions,
    private val httpMonitorActions: HttpMonitorActions,
    private val statusPageRepository: StatusPageRepository,
) : BehaviorSpec({

    given("the getDefaultStatusPageData() method") {

        `when`("there is a pending monitor coming from the data providers") {

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = null,
                    averageLatencyInMs = null,
                    uptimeRatio = null,
                    uptimeStatus = null,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return PENDING as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }

        `when`("there is an unhealthy monitor coming from the data providers") {

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return PARTIAL_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PARTIAL_OUTAGE
            }
        }

        `when`("all the monitors are unhealthy") {

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return MAJOR_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.MAJOR_OUTAGE
            }
        }

        `when`("all the monitors are healthy") {

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return OPERATIONAL as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.OPERATIONAL
            }
        }

        `when`("there is no monitor assigned to the status page") {

            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns emptyList()

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should return PENDING as system status") {

                result.monitors shouldBe emptyList()
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }
    }

    given("the getStatusPageData(statusPageId) method") {

        fun statusPageRecord() = StatusPageRecord().apply {
            id = 1L
            slug = "example-status"
            title = "Something custom"
            customLogoUrl = "https://custom.logo"
            customFaviconUrl = "https://custom.favicon"
            public = true
            monitors = listOf(
                MonitorID(MonitorType.HTTP_SSL, "test-monitor-1"),
                MonitorID(MonitorType.HTTP_SSL, "test-monitor-2"),
            ).toTypedArray()
            createdAt = getCurrentTimestamp()
            updatedAt = getCurrentTimestamp()
        }

        `when`("the given status page does not exist") {
            val invalidStatusPageId = 9999L
            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(invalidStatusPageId, any()) } returns null

            val ex = shouldThrow<StatusPageNotFoundException> {
                statusPageActions.getStatusPageData(invalidStatusPageId)
            }

            then("it should throw StatusPageNotFoundException") {
                ex.statusPageId shouldBe invalidStatusPageId
            }
        }

        `when`("there is a pending monitor coming from the data providers") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = null,
                    averageLatencyInMs = null,
                    uptimeRatio = null,
                    uptimeStatus = null,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return PENDING as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Something custom"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }

        `when`("there is an unhealthy monitor coming from the data providers") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return PARTIAL_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Something custom"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PARTIAL_OUTAGE
            }
        }

        `when`("all the monitors are unhealthy") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return MAJOR_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Something custom"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.MAJOR_OUTAGE
            }
        }

        `when`("all the monitors are healthy") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockMonitorList = listOf(
                StatusPageMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageMonitorDetailsDto(
                    name = "Arya Stark",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9234,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 1,
                        )
                    )
                ),
            )
            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return OPERATIONAL as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Something custom"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.OPERATIONAL
            }
        }

        `when`("there is no monitor assigned to the status page") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord().apply { monitors = emptyArray() }

            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getDataOfEnabledMonitors(Duration.ofDays(30), emptyList())
            } returns emptyList()

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should return PENDING as system status") {

                result.monitors shouldBe emptyList()
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }
    }
}) {
    @MockBean(HttpMonitorActions::class)
    fun httpMonitorActions(): HttpMonitorActions = mockk()

    @MockBean(StatusPageRepository::class)
    fun statusPageRepository(): StatusPageRepository = mockk()
}
