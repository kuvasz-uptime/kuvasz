package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.StatusPageRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.StatusPageNotFoundException
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPagePushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.repositories.StatusPageRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
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
    private val pushMonitorActions: PushMonitorActions,
    private val icmpMonitorActions: IcmpMonitorActions,
    private val statusPageRepository: StatusPageRepository,
) : BehaviorSpec({

    given("the getDefaultStatusPageData() method") {

        `when`("there is a pending monitor coming from the data providers") {

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = null,
                    averageLatencyInMs = null,
                    uptimeRatio = null,
                    uptimeStatus = null,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockPushMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return PENDING as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }

        `when`("there is an unhealthy monitor coming from the data providers") {

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockPushMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return PARTIAL_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PARTIAL_OUTAGE
            }
        }

        `when`("all the monitors are unhealthy") {

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockPushMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return MAJOR_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.MAJOR_OUTAGE
            }
        }

        `when`("all the monitors are healthy") {

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockPushMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should fetch the monitors from the providers and return OPERATIONAL as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
                result.title shouldBe "Custom System Status"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.OPERATIONAL
            }
        }

        `when`("there is no monitor assigned to the status page") {

            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns emptyList()
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns emptyList()

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should return PENDING as system status") {

                result.monitors shouldBe emptyList()
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }

        `when`("there are ICMP monitors alongside healthy HTTP monitors") {

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "HTTP monitor",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 50,
                    uptimeRatio = 1.0,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList(),
                ),
            )
            val mockIcmpMonitorList = listOf(
                StatusPageIcmpMonitorDetailsDto(
                    name = "ICMP monitor pending",
                    lastCheck = null,
                    averageLatencyInMs = null,
                    lastPacketLossPercentage = null,
                    uptimeRatio = null,
                    uptimeStatus = null,
                    uptimeStatusHistory = emptyList(),
                ),
                StatusPageIcmpMonitorDetailsDto(
                    name = "ICMP monitor up",
                    lastCheck = getCurrentTimestamp().minusSeconds(5),
                    averageLatencyInMs = 12,
                    lastPacketLossPercentage = 0,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 0,
                        )
                    ),
                ),
            )

            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns emptyList()
            val mockIcmpMonitorActions = getMock(icmpMonitorActions)
            every {
                mockIcmpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockIcmpMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should include ICMP monitors in the result and return PENDING as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockIcmpMonitorList
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }

        `when`("a DOWN ICMP monitor is mixed with UP HTTP monitors") {

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "HTTP monitor",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 50,
                    uptimeRatio = 1.0,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList(),
                ),
            )
            val mockIcmpMonitorList = listOf(
                StatusPageIcmpMonitorDetailsDto(
                    name = "ICMP monitor down",
                    lastCheck = getCurrentTimestamp().minusSeconds(5),
                    averageLatencyInMs = null,
                    lastPacketLossPercentage = 100,
                    uptimeRatio = 0.95,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 2,
                        )
                    ),
                ),
            )

            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns emptyList()
            val mockIcmpMonitorActions = getMock(icmpMonitorActions)
            every {
                mockIcmpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), null)
            } returns mockIcmpMonitorList

            val result = statusPageActions.getDefaultStatusPageData()

            then("it should include the ICMP monitor in the result and return PARTIAL_OUTAGE") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockIcmpMonitorList
                result.systemStatus shouldBe SystemStatus.PARTIAL_OUTAGE
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
                MonitorID(MonitorType.PUSH, "test-monitor-3"),
                MonitorID(MonitorType.PUSH, "test-monitor-4"),
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
                ex.statusPageId shouldBe invalidStatusPageId.toString()
            }
        }

        `when`("there is a pending monitor coming from the data providers") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = null,
                    averageLatencyInMs = null,
                    uptimeRatio = null,
                    uptimeStatus = null,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = null,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockPushMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return PENDING as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
                result.title shouldBe "Something custom"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }

        `when`("there is an unhealthy monitor coming from the data providers") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockPushMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return PARTIAL_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
                result.title shouldBe "Something custom"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PARTIAL_OUTAGE
            }
        }

        `when`("all the monitors are unhealthy") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockPushMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return MAJOR_OUTAGE as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
                result.title shouldBe "Something custom"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.MAJOR_OUTAGE
            }
        }

        `when`("all the monitors are healthy") {

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(1L, any()) } returns statusPageRecord()

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "Josh Snow",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 123,
                    uptimeRatio = 0.9999,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList(),
                ),
                StatusPageHttpMonitorDetailsDto(
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
            val mockPushMonitorList = listOf(
                StatusPagePushMonitorDetailsDto(
                    name = "One of the dragon lords",
                    lastCheck = null,
                    lastHeartbeat = null,
                    uptimeRatio = null,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList()
                ),
                StatusPagePushMonitorDetailsDto(
                    name = "I can't recall more from the series",
                    lastCheck = getCurrentTimestamp().minusSeconds(3),
                    lastHeartbeat = null,
                    uptimeRatio = 0.9231,
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    statusPageRecord().monitors?.toList(),
                )
            } returns mockPushMonitorList

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should fetch the monitors from the providers and return OPERATIONAL as system status") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockPushMonitorList
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
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), emptyList())
            } returns emptyList()
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(Duration.ofDays(30), emptyList())
            } returns emptyList()

            val result = statusPageActions.getStatusPageData(statusPageRecord().id)

            then("it should return PENDING as system status") {

                result.monitors shouldBe emptyList()
                result.systemStatus shouldBe SystemStatus.PENDING
            }
        }

        `when`("there are ICMP monitors assigned to the status page") {

            fun icmpStatusPageRecord() = StatusPageRecord().apply {
                id = 2L
                slug = "icmp-status"
                title = "ICMP Status"
                customLogoUrl = null
                customFaviconUrl = null
                public = true
                monitors = listOf(
                    MonitorID(MonitorType.HTTP_SSL, "http-monitor-1"),
                    MonitorID(MonitorType.ICMP, "icmp-monitor-1"),
                    MonitorID(MonitorType.ICMP, "icmp-monitor-2"),
                ).toTypedArray()
                createdAt = getCurrentTimestamp()
                updatedAt = getCurrentTimestamp()
            }

            val repoMock = getMock(statusPageRepository)
            every { repoMock.findById(2L, any()) } returns icmpStatusPageRecord()

            val mockHttpMonitorList = listOf(
                StatusPageHttpMonitorDetailsDto(
                    name = "http-monitor-1",
                    lastCheck = getCurrentTimestamp().minusSeconds(10),
                    averageLatencyInMs = 80,
                    uptimeRatio = 1.0,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = emptyList(),
                ),
            )
            val mockIcmpMonitorList = listOf(
                StatusPageIcmpMonitorDetailsDto(
                    name = "icmp-monitor-1",
                    lastCheck = getCurrentTimestamp().minusSeconds(5),
                    averageLatencyInMs = 15,
                    lastPacketLossPercentage = 0,
                    uptimeRatio = 1.0,
                    uptimeStatus = UptimeStatus.UP,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 0,
                        )
                    ),
                ),
                StatusPageIcmpMonitorDetailsDto(
                    name = "icmp-monitor-2",
                    lastCheck = getCurrentTimestamp().minusSeconds(5),
                    averageLatencyInMs = null,
                    lastPacketLossPercentage = 100,
                    uptimeRatio = 0.9,
                    uptimeStatus = UptimeStatus.DOWN,
                    uptimeStatusHistory = listOf(
                        StatusHistoryDto(
                            date = getCurrentTimestamp().minusDays(1).toLocalDate(),
                            outageCnt = 3,
                        )
                    ),
                ),
            )

            val mockHttpMonitorActions = getMock(httpMonitorActions)
            every {
                mockHttpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    icmpStatusPageRecord().monitors?.toList(),
                )
            } returns mockHttpMonitorList
            val mockPushMonitorActions = getMock(pushMonitorActions)
            every {
                mockPushMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    icmpStatusPageRecord().monitors?.toList(),
                )
            } returns emptyList()
            val mockIcmpMonitorActions = getMock(icmpMonitorActions)
            every {
                mockIcmpMonitorActions.getStatusPageDataOfEnabledMonitors(
                    Duration.ofDays(30),
                    icmpStatusPageRecord().monitors?.toList(),
                )
            } returns mockIcmpMonitorList

            val result = statusPageActions.getStatusPageData(icmpStatusPageRecord().id)

            then("it should include ICMP monitors in the result with correct fields and return PARTIAL_OUTAGE") {

                result.monitors shouldContainExactlyInAnyOrder mockHttpMonitorList + mockIcmpMonitorList
                result.systemStatus shouldBe SystemStatus.PARTIAL_OUTAGE
                result.title shouldBe "ICMP Status"

                val icmpUp = result.monitors.filterIsInstance<StatusPageIcmpMonitorDetailsDto>()
                    .first { it.name == "icmp-monitor-1" }
                icmpUp.averageLatencyInMs shouldBe 15
                icmpUp.lastPacketLossPercentage shouldBe 0
                icmpUp.uptimeRatio shouldBe 1.0
                icmpUp.uptimeStatus shouldBe UptimeStatus.UP

                val icmpDown = result.monitors.filterIsInstance<StatusPageIcmpMonitorDetailsDto>()
                    .first { it.name == "icmp-monitor-2" }
                icmpDown.averageLatencyInMs shouldBe null
                icmpDown.lastPacketLossPercentage shouldBe 100
                icmpDown.uptimeRatio shouldBe 0.9
                icmpDown.uptimeStatus shouldBe UptimeStatus.DOWN
            }
        }
    }
}) {
    @MockBean(HttpMonitorActions::class)
    fun httpMonitorActions(): HttpMonitorActions = mockk()

    @MockBean(PushMonitorActions::class)
    fun pushMonitorActions(): PushMonitorActions = mockk()

    @MockBean(IcmpMonitorActions::class)
    fun icmpMonitorActions(): IcmpMonitorActions = mockk {
        every { getStatusPageDataOfEnabledMonitors(any(), any()) } returns emptyList()
    }

    @MockBean(StatusPageRepository::class)
    fun statusPageRepository(): StatusPageRepository = mockk()
}
