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
import io.mockk.verify
import java.time.Duration

@MicronautTest(environments = ["full-default-status-page-config"])
class StatusPageCacheTest(
    private val statusPageActions: StatusPageDataActions,
    private val httpMonitorActions: HttpMonitorActions,
    private val statusPageRepository: StatusPageRepository,
) : BehaviorSpec({

    given("the getCachedDefaultStatusPageData() method") {

        `when`("it is called multiple times") {

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

            val result = statusPageActions.getCachedDefaultStatusPageData()
            val result2 = statusPageActions.getCachedDefaultStatusPageData()

            then("it should execute the logic inside only once") {

                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result shouldBe result2
                verify(exactly = 1) {
                    mockHttpMonitorActions.getDataOfEnabledMonitors(any(), any())
                }
            }
        }
    }

    given("the getCachedStatusPageData(statusPageId) method") {

        fun statusPageRecord() = StatusPageRecord().apply {
            id = 1L
            slug = "example-status"
            title = "Example status page"
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
                statusPageActions.getCachedStatusPageData(invalidStatusPageId)
            }
            val ex2 = shouldThrow<StatusPageNotFoundException> {
                statusPageActions.getCachedStatusPageData(invalidStatusPageId)
            }

            then("it should throw StatusPageNotFoundException but not cache the result") {

                verify(exactly = 2) { repoMock.findById(invalidStatusPageId, any()) }
                ex shouldBe ex2
                ex.statusPageId shouldBe invalidStatusPageId
            }
        }

        `when`("the given status page exists") {

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

            val result = statusPageActions.getCachedStatusPageData(statusPageRecord().id)
            val result2 = statusPageActions.getCachedStatusPageData(statusPageRecord().id)

            then("it should return the data of it and cache the result") {

                result shouldBe result2
                result.monitors shouldContainExactlyInAnyOrder mockMonitorList
                result.title shouldBe "Example status page"
                result.customLogoUrl shouldBe "https://custom.logo"
                result.customFaviconUrl shouldBe "https://custom.favicon"
                result.systemStatus shouldBe SystemStatus.PENDING
                verify(exactly = 1) {
                    repoMock.findById(any(), any())
                    mockHttpMonitorActions.getDataOfEnabledMonitors(any(), any())
                }
            }
        }
    }
}) {
    @MockBean(HttpMonitorActions::class)
    fun httpMonitorActions(): HttpMonitorActions = mockk()

    @MockBean(StatusPageRepository::class)
    fun statusPageRepository(): StatusPageRepository = mockk()
}
