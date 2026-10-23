package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.testutils.SqlRecorder
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class HttpMonitorRepositoryTest(
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the fetchWithProxyNotIn() method") {

            `when`("there are direct monitors, and ones with a configured and a dangling proxy") {
                createHttpMonitor(httpMonitorRepository, monitorName = "direct")
                createHttpMonitor(httpMonitorRepository, monitorName = "proxied", proxy = "egress")
                createHttpMonitor(httpMonitorRepository, monitorName = "dangling", proxy = "removed")

                then("it should only return the ones whose proxy is not among the given ones") {
                    val names = httpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("dangling")
                }

                then("it should return every proxied monitor, when no proxy is given") {
                    val names = httpMonitorRepository.fetchWithProxyNotIn(emptySet()).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("proxied", "dangling")
                }
            }

            `when`("every proxy of the monitors is given") {
                createHttpMonitor(httpMonitorRepository, proxy = "egress")

                then("it should return nothing") {
                    httpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).shouldBeEmpty()
                }
            }
        }

        given("the getMonitorsPage() method") {

            `when`("the page is full, so the monitors have to be counted") {
                val monitors = (1..3).map { createHttpMonitor(httpMonitorRepository) }
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitors.first().name)))
                monitors.forEach { monitor ->
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = monitor.id,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp(),
                        endedAt = null,
                    )
                }

                val unfiltered = SqlRecorder(dslContext)
                val unfilteredPage = fetchPage(unfiltered, uptimeStatus = emptyList(), size = 2)
                val byStatus = SqlRecorder(dslContext)
                val byStatusPage = fetchPage(byStatus, uptimeStatus = listOf(UptimeStatus.DOWN), size = 2)

                then("it should count every matching monitor") {
                    unfilteredPage.totalCount shouldBe 3
                    byStatusPage.totalCount shouldBe 3
                }

                then("the count should read nothing but the monitors, and what the filters need") {
                    val count = unfiltered.countStatements.single()

                    count shouldContain "\"http_monitor\""
                    count shouldContain "\"ssl_event\""
                    count shouldNotContain "\"status_page\""
                    count shouldNotContain "\"http_uptime_event\""
                    count shouldNotContain "\"name\""
                }

                then("filtering by the uptime status should join the latest uptime event, but nothing else") {
                    val count = byStatus.countStatements.single()

                    count shouldContain "\"http_uptime_event\""
                    count shouldNotContain "\"status_page\""
                    count shouldNotContain "\"name\""
                }
            }

            `when`("the page is not full") {
                repeat(3) { createHttpMonitor(httpMonitorRepository) }

                val recorder = SqlRecorder(dslContext)
                val page = fetchPage(recorder, uptimeStatus = emptyList(), size = 5)

                then("it should tell the total from the page itself, without counting") {
                    page.totalCount shouldBe 3
                    recorder.countStatements.shouldBeEmpty()
                }
            }

            `when`("the page is after the last one") {
                repeat(3) { createHttpMonitor(httpMonitorRepository) }

                val recorder = SqlRecorder(dslContext)
                val page = fetchPage(recorder, uptimeStatus = emptyList(), size = 2, page = 3)

                then("it should count the monitors, since an empty page can't tell") {
                    page.result.shouldBeEmpty()
                    page.totalCount shouldBe 3
                    recorder.countStatements shouldHaveSize 1
                }
            }
        }
    }

    private fun fetchPage(
        recorder: SqlRecorder,
        uptimeStatus: List<UptimeStatus>,
        size: Int,
        page: Int = 1,
    ) = HttpMonitorRepository(recorder.dsl).getMonitorsPage(
        enabled = null,
        uptimeStatus = uptimeStatus,
                sslStatus = emptyList(),
                sslCheckEnabled = null,
        categoryFilter = null,
        name = null,
        sorting = Sorting(HttpMonitorSortField.NAME, SortOrder.ASC),
        pageRequest = PageRequest(page = page, size = size),
    )
}
