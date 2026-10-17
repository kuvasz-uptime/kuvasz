package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.monitor.DnsMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import java.time.OffsetDateTime

@MicronautTest(environments = ["full-integrations-setup"])
class DnsMonitorV3ControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorClient: DnsMonitorV3Client,
    private val v2MonitorClient: DnsMonitorClient,
    private val monitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the v3 getMonitorsPage() endpoint") {

            `when`("there are more monitors than fit on a page") {
                // Created in a different order than their names, so the default sorting by name can be told apart
                val ids = listOf("e", "c", "a", "d", "b").associateWith {
                    createDnsMonitor(monitorRepository, monitorName = it).id
                }

                val firstPage = fetchPage(page = 1, size = 2)
                val lastPage = fetchPage(page = 3, size = 2)
                val pageAfterTheLast = fetchPage(page = 4, size = 2)

                then("it should return the requested page, sorted by the name by default") {
                    firstPage.result.map { it.id } shouldContainExactly listOf(ids["a"], ids["b"])
                    firstPage.totalCount shouldBe 5
                    firstPage.totalPages shouldBe 3
                }

                then("the last page should hold only the remaining monitors") {
                    lastPage.result.map { it.id } shouldContainExactly listOf(ids["e"])
                }

                then("a page after the last one should be empty, with the same totals") {
                    pageAfterTheLast.result.shouldBeEmpty()
                    pageAfterTheLast.totalCount shouldBe 5
                }
            }

            `when`("the monitors are sorted by any of the sortable fields") {
                // Every field puts these in a different order, see shouldTellTheSortFieldsApart below
                val now = getCurrentTimestamp()
                val seededIds = listOf(
                    seedMonitor(
                        now, "Bravo", enabled = true, uptimeCheckInterval = 60, host = "b.example.com",
                        uptime = SeededUptime(UptimeStatus.UP, startedMinutesAgo = 50, lastCheckMinutesAgo = 1),
                        createdMinutesAgo = 10, updatedMinutesAgo = 5,
                    ),
                    seedMonitor(
                        now, "alpha", enabled = false, uptimeCheckInterval = 30, host = "a.example.com",
                        uptime = SeededUptime(UptimeStatus.DOWN, startedMinutesAgo = 10, lastCheckMinutesAgo = 2),
                        createdMinutesAgo = 40, updatedMinutesAgo = 5,
                    ),
                    // Without any event, so every field of the checks is empty
                    seedMonitor(
                        now, "echo", enabled = true, uptimeCheckInterval = 60, host = "C.example.com",
                        uptime = null,
                        createdMinutesAgo = 20, updatedMinutesAgo = 60,
                    ),
                    seedMonitor(
                        now, "Delta", enabled = false, uptimeCheckInterval = 120, host = "d.example.com",
                        uptime = SeededUptime(UptimeStatus.UP, startedMinutesAgo = 20, lastCheckMinutesAgo = 1),
                        createdMinutesAgo = 30, updatedMinutesAgo = 1,
                    ),
                    seedMonitor(
                        now, "charlie", enabled = true, uptimeCheckInterval = 90, host = "a.example.com",
                        uptime = SeededUptime(UptimeStatus.DOWN, startedMinutesAgo = 30, lastCheckMinutesAgo = 5),
                        createdMinutesAgo = 50, updatedMinutesAgo = 30,
                    ),
                )

                val monitors = fetchPage().result
                val keys = mapOf<DnsMonitorSortField, (DnsMonitorDetailsDto) -> Comparable<*>?>(
                    DnsMonitorSortField.ID to { it.id },
                    DnsMonitorSortField.NAME to { it.name.lowercase() },
                    DnsMonitorSortField.ENABLED to { it.enabled },
                    DnsMonitorSortField.UPTIME_STATUS to { it.uptimeStatus },
                    DnsMonitorSortField.UPTIME_STATUS_STARTED_AT to { it.uptimeStatusStartedAt?.toInstant() },
                    DnsMonitorSortField.LAST_UPTIME_CHECK to { it.lastUptimeCheck?.toInstant() },
                    DnsMonitorSortField.UPTIME_CHECK_INTERVAL to { it.uptimeCheckInterval },
                    DnsMonitorSortField.HOST to { it.host.lowercase() },
                    DnsMonitorSortField.CREATED_AT to { it.createdAt.toInstant() },
                    DnsMonitorSortField.UPDATED_AT to { it.updatedAt.toInstant() },
                )

                then("the expectations should be calculated from every seeded monitor") {
                    monitors.map { it.id } shouldContainExactlyInAnyOrder seededIds
                }

                then("every field should be covered by the test") {
                    keys.keys shouldBe DnsMonitorSortField.entries.toSet()
                }

                then("the seeded monitors should tell every field apart") {
                    monitors.shouldTellTheSortFieldsApart(keys)
                }

                then("they should be sorted in both orders, the ones without a value last, the same values by ID") {
                    keys.forEach { (field, key) ->
                        SortOrder.entries.forEach { order ->
                            withClue("$field $order") {
                                fetchPage(sortedBy = field, sortOrder = order).result.map { it.id } shouldBe
                                    monitors.expectedIdsSortedBy(order, key)
                            }
                        }
                    }
                }

                then("walking the pages should return the same order, without any monitor twice or missing") {
                    val walkedIds = (1..3).flatMap { page ->
                        fetchPage(
                            page = page,
                            size = 2,
                            sortedBy = DnsMonitorSortField.ENABLED,
                            sortOrder = SortOrder.DESC,
                        ).result.map { it.id }
                    }

                    walkedIds shouldBe monitors.expectedIdsSortedBy(SortOrder.DESC) { it.enabled }
                }

                then("a field without an order should be sorted in ascending order") {
                    fetchPage(sortedBy = DnsMonitorSortField.HOST).result.map { it.id } shouldBe
                        monitors.expectedIdsSortedBy(SortOrder.ASC) { it.host.lowercase() }
                }

                then("an order without a field should sort by the name") {
                    fetchPage(sortOrder = SortOrder.DESC).result.map { it.id } shouldBe
                        monitors.expectedIdsSortedBy(SortOrder.DESC) { it.name.lowercase() }
                }
            }

            `when`("the monitors are filtered by their name") {
                listOf("Prod API", "prod-web", "staging", "100%_up").forEach {
                    createDnsMonitor(monitorRepository, monitorName = it)
                }

                then("it should match a part of the name, without regard to the case of the letters") {
                    fetchPage(name = "PROD").result.map { it.name } shouldContainExactly listOf("Prod API", "prod-web")
                }

                then("the wildcards of LIKE should be matched literally") {
                    fetchPage(name = "_").result.map { it.name } shouldContainExactly listOf("100%_up")
                }
            }

            `when`("the other filters are combined with the paging") {
                createDnsMonitor(monitorRepository, monitorName = "shop-1", category = "shop")
                createDnsMonitor(monitorRepository, monitorName = "shop-2", category = "shop")
                val disabledShop =
                    createDnsMonitor(monitorRepository, monitorName = "shop-3", category = "shop", enabled = false)
                createDnsUptimeEventRecord(
                    dslContext,
                    monitorId = disabledShop.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                val down = createDnsMonitor(monitorRepository, monitorName = "no-category")
                createDnsUptimeEventRecord(
                    dslContext,
                    monitorId = down.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )

                then("the totals should count only the matching monitors") {
                    val page = fetchPage(enabled = true, category = "shop", size = 1)

                    page.result.map { it.name } shouldContainExactly listOf("shop-1")
                    page.totalCount shouldBe 2
                    page.totalPages shouldBe 2
                }

                then("an empty category should select the monitors without one") {
                    fetchPage(category = "").result.map { it.id } shouldBe listOf(down.id)
                }

                then("the uptime status filter should be applied too") {
                    fetchPage(uptimeStatus = listOf(UptimeStatus.DOWN)).result.map { it.id } shouldBe
                        listOf(down.id, disabledShop.id)
                }

                // A full page can't tell the total itself, so this runs the count query with the join of the filter
                then("the totals of a full page should count with the uptime status filter too") {
                    fetchPage(uptimeStatus = listOf(UptimeStatus.DOWN), size = 1).totalCount shouldBe 2
                }
            }

            `when`("only some of the monitors are affected by a maintenance window") {
                val affected = createDnsMonitor(monitorRepository)
                val notAffected = createDnsMonitor(monitorRepository)
                createMaintenanceWindow(dslContext, monitors = listOf(MonitorID(MonitorType.DNS, affected.name)))

                val page = fetchPage().result

                then("each monitor should get its own details, the same as the v2 endpoint returns") {
                    page.single { it.id == affected.id }.maintenanceWindows.shouldNotBeEmpty()
                    page.single { it.id == notAffected.id }.maintenanceWindows.shouldBeEmpty()
                    page.forEach { it.effectiveIntegrations.shouldNotBeEmpty() }
                    page.sortedBy { it.id } shouldBe v2MonitorClient.getMonitorsWithDetails(
                        enabled = null,
                        uptimeStatus = null,
                        category = null,
                    ).sortedBy { it.id }
                }
            }

            `when`("the parameters are invalid") {
                val invalidQueries = listOf("page=0", "size=0", "sortedBy=NOPE", "sortOrder=SIDEWAYS")

                then("it should return 400") {
                    invalidQueries.forEach { query ->
                        withClue(query) {
                            val exception = shouldThrow<HttpClientResponseException> {
                                client.exchange("/api/v3/dns-monitors?$query").awaitFirst()
                            }
                            exception.status shouldBe HttpStatus.BAD_REQUEST
                        }
                    }
                }
            }
        }
    }

    private fun seedMonitor(
        now: OffsetDateTime,
        name: String,
        enabled: Boolean,
        uptimeCheckInterval: Int,
        host: String,
        uptime: SeededUptime?,
        createdMinutesAgo: Long,
        updatedMinutesAgo: Long,
    ): Long {
        val monitor = createDnsMonitor(
            monitorRepository,
            monitorName = name,
            enabled = enabled,
            uptimeCheckInterval = uptimeCheckInterval,
            host = host,
        )
        uptime?.let { seeded ->
            createDnsUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = seeded.status,
                startedAt = now.minusMinutes(seeded.startedMinutesAgo),
                updatedAt = now.minusMinutes(seeded.lastCheckMinutesAgo),
                endedAt = null,
            )
        }
        dslContext.setCreatedAndUpdatedAt(
            idField = DNS_MONITOR.ID,
            createdAtField = DNS_MONITOR.CREATED_AT,
            updatedAtField = DNS_MONITOR.UPDATED_AT,
            monitorId = monitor.id,
            createdAt = now.minusMinutes(createdMinutesAgo),
            updatedAt = now.minusMinutes(updatedMinutesAgo),
        )
        return monitor.id
    }

    private fun fetchPage(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus>? = null,
        category: String? = null,
        name: String? = null,
        page: Int = 1,
        size: Int = 100,
        sortedBy: DnsMonitorSortField? = null,
        sortOrder: SortOrder? = null,
    ) = monitorClient.getMonitorsPage(
        enabled = enabled,
        uptimeStatus = uptimeStatus,
        category = category,
        name = name,
        page = page,
        size = size,
        sortedBy = sortedBy,
        sortOrder = sortOrder,
    )
}
