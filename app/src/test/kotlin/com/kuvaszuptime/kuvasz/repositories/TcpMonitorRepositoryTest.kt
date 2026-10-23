package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorSortField
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
class TcpMonitorRepositoryTest(
    private val tcpMonitorRepository: TcpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the fetchWithProxyNotIn() method") {

            `when`("there are direct monitors, and ones with a configured and a dangling proxy") {
                createTcpMonitor(tcpMonitorRepository, monitorName = "direct")
                createTcpMonitor(tcpMonitorRepository, monitorName = "proxied", proxy = "egress")
                createTcpMonitor(tcpMonitorRepository, monitorName = "dangling", proxy = "removed")

                then("it should only return the ones whose proxy is not among the given ones") {
                    val names = tcpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("dangling")
                }

                then("it should return every proxied monitor, when no proxy is given") {
                    val names = tcpMonitorRepository.fetchWithProxyNotIn(emptySet()).map { it.name }
                    names shouldContainExactlyInAnyOrder listOf("proxied", "dangling")
                }
            }

            `when`("every proxy of the monitors is given") {
                createTcpMonitor(tcpMonitorRepository, proxy = "egress")

                then("it should return nothing") {
                    tcpMonitorRepository.fetchWithProxyNotIn(setOf("egress")).shouldBeEmpty()
                }
            }
        }

        given("the getMonitorsPage() method") {

            `when`("the page is full, so the monitors have to be counted") {
                val monitors = (1..3).map { createTcpMonitor(tcpMonitorRepository) }
                createStatusPage(dslContext, monitors = listOf(MonitorID(MonitorType.TCP, monitors.first().name)))
                monitors.forEach { monitor ->
                    createTcpUptimeEventRecord(
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

                    count shouldContain "\"tcp_monitor\""
                    count shouldNotContain "\"status_page\""
                    count shouldNotContain "\"tcp_uptime_event\""
                    count shouldNotContain "\"name\""
                }

                then("filtering by the uptime status should join the latest uptime event, but nothing else") {
                    val count = byStatus.countStatements.single()

                    count shouldContain "\"tcp_uptime_event\""
                    count shouldNotContain "\"status_page\""
                    count shouldNotContain "\"name\""
                }
            }

            `when`("the page is not full") {
                repeat(3) { createTcpMonitor(tcpMonitorRepository) }

                val recorder = SqlRecorder(dslContext)
                val page = fetchPage(recorder, uptimeStatus = emptyList(), size = 5)

                then("it should tell the total from the page itself, without counting") {
                    page.totalCount shouldBe 3
                    recorder.countStatements.shouldBeEmpty()
                }
            }

            `when`("the page is after the last one") {
                repeat(3) { createTcpMonitor(tcpMonitorRepository) }

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
    ) = TcpMonitorRepository(recorder.dsl).getMonitorsPage(
        enabled = null,
        uptimeStatus = uptimeStatus,
        categoryFilter = null,
        name = null,
        sorting = Sorting(TcpMonitorSortField.NAME, SortOrder.ASC),
        pageRequest = PageRequest(page = page, size = size),
    )
}
