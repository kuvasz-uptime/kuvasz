package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.event.EventSortField
import com.kuvaszuptime.kuvasz.models.dto.event.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime

@MicronautTest(environments = ["full-integrations-setup"])
class HttpMonitorV3ControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val monitorClient: HttpMonitorV3Client,
    private val v2MonitorClient: HttpMonitorClient,
    private val monitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    private val mapper = jacksonObjectMapper()

    init {
        given("the v3 getMonitorsPage() endpoint") {

            `when`("there are more monitors than fit on a page") {
                // Created in a different order than their names, so the default sorting by name can be told apart
                val ids = listOf("e", "c", "a", "d", "b").associateWith {
                    createHttpMonitor(monitorRepository, monitorName = it).id
                }

                val firstPage = fetchPage(page = 1, size = 2)
                val lastPage = fetchPage(page = 3, size = 2)
                val pageAfterTheLast = fetchPage(page = 4, size = 2)

                then("it should return the requested page, sorted by the name by default") {
                    firstPage.result.map { it.id } shouldContainExactly listOf(ids["a"], ids["b"])
                    firstPage.page shouldBe 1
                    firstPage.size shouldBe 2
                    firstPage.totalCount shouldBe 5
                    firstPage.totalPages shouldBe 3
                }

                then("the last page should hold only the remaining monitors") {
                    lastPage.result.map { it.id } shouldContainExactly listOf(ids["e"])
                    lastPage.totalPages shouldBe 3
                }

                then("a page after the last one should be empty, with the same totals") {
                    pageAfterTheLast.result.shouldBeEmpty()
                    pageAfterTheLast.page shouldBe 4
                    pageAfterTheLast.totalCount shouldBe 5
                    pageAfterTheLast.totalPages shouldBe 3
                }

                then("the number of pages should not be rounded up when the monitors fill the last page exactly") {
                    fetchPage(size = 5).totalPages shouldBe 1
                    fetchPage(size = 1).totalPages shouldBe 5
                }
            }

            `when`("there are more monitors than the default page size") {
                val ids = (1..27).map { index ->
                    // Zero padded, so the order of the names is the order of the creation
                    val name = "monitor-${index.toString().padStart(2, '0')}"
                    createHttpMonitor(monitorRepository, monitorName = name).id
                }

                val response = client.exchange("/api/v3/http-monitors", String::class.java).awaitFirst()
                val page = mapper.readValue<PagedResult<HttpMonitorDetailsDto>>(checkNotNull(response.body()))

                then("without any parameter, it should return the first 25 of them") {
                    page.page shouldBe 1
                    page.size shouldBe 25
                    page.result.map { it.id } shouldContainExactly ids.take(25)
                    page.totalCount shouldBe 27
                    page.totalPages shouldBe 2
                }
            }

            `when`("there is no monitor at all") {
                val page = fetchPage()

                then("it should return an empty page without any pages in total") {
                    page.result.shouldBeEmpty()
                    page.totalCount shouldBe 0
                    page.totalPages shouldBe 0
                }
            }

            `when`("the monitors are sorted by any of the sortable fields") {
                // Every field puts these in a different order, see shouldTellTheSortFieldsApart below
                val now = getCurrentTimestamp()
                val seededIds = listOf(
                    seedMonitor(
                        now, "Bravo", enabled = true, url = "http://b.example.com", uptimeCheckInterval = 60,
                        uptime = SeededUptime(UptimeStatus.UP, startedMinutesAgo = 50, lastCheckMinutesAgo = 1),
                        ssl = SslStatus.VALID to 30L, createdMinutesAgo = 10, updatedMinutesAgo = 5,
                    ),
                    seedMonitor(
                        now, "alpha", enabled = false, url = "http://a.example.com", uptimeCheckInterval = 30,
                        uptime = SeededUptime(UptimeStatus.DOWN, startedMinutesAgo = 10, lastCheckMinutesAgo = 2),
                        ssl = SslStatus.INVALID to null, createdMinutesAgo = 40, updatedMinutesAgo = 5,
                    ),
                    // Without any event, so every field of the checks is empty
                    seedMonitor(
                        now, "echo", enabled = true, url = "http://C.example.com", uptimeCheckInterval = 60,
                        uptime = null, ssl = null, createdMinutesAgo = 20, updatedMinutesAgo = 60,
                    ),
                    seedMonitor(
                        now, "Delta", enabled = false, url = "http://d.example.com", uptimeCheckInterval = 120,
                        uptime = SeededUptime(UptimeStatus.UP, startedMinutesAgo = 20, lastCheckMinutesAgo = 1),
                        ssl = SslStatus.WILL_EXPIRE to 3L, createdMinutesAgo = 30, updatedMinutesAgo = 1,
                    ),
                    seedMonitor(
                        now, "charlie", enabled = true, url = "http://e.example.com", uptimeCheckInterval = 90,
                        uptime = SeededUptime(UptimeStatus.DOWN, startedMinutesAgo = 30, lastCheckMinutesAgo = 5),
                        ssl = SslStatus.VALID to 10L, createdMinutesAgo = 50, updatedMinutesAgo = 30,
                    ),
                )

                val monitors = fetchPage().result
                val keys = mapOf<HttpMonitorSortField, (HttpMonitorDetailsDto) -> Comparable<*>?>(
                    HttpMonitorSortField.ID to { it.id },
                    HttpMonitorSortField.NAME to { it.name.lowercase() },
                    HttpMonitorSortField.ENABLED to { it.enabled },
                    HttpMonitorSortField.UPTIME_STATUS to { it.uptimeStatus },
                    HttpMonitorSortField.UPTIME_STATUS_STARTED_AT to { it.uptimeStatusStartedAt?.toInstant() },
                    HttpMonitorSortField.LAST_UPTIME_CHECK to { it.lastUptimeCheck?.toInstant() },
                    HttpMonitorSortField.UPTIME_CHECK_INTERVAL to { it.uptimeCheckInterval },
                    HttpMonitorSortField.URL to { it.url.toString().lowercase() },
                    HttpMonitorSortField.SSL_STATUS to { it.sslStatus },
                    HttpMonitorSortField.SSL_VALID_UNTIL to { it.sslValidUntil?.toInstant() },
                    HttpMonitorSortField.CREATED_AT to { it.createdAt.toInstant() },
                    HttpMonitorSortField.UPDATED_AT to { it.updatedAt?.toInstant() },
                )

                then("the expectations should be calculated from every seeded monitor") {
                    monitors.map { it.id } shouldContainExactlyInAnyOrder seededIds
                }

                then("every field should be covered by the test") {
                    keys.keys shouldBe HttpMonitorSortField.entries.toSet()
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
                            sortedBy = HttpMonitorSortField.ENABLED,
                            sortOrder = SortOrder.DESC,
                        ).result.map { it.id }
                    }

                    walkedIds shouldBe monitors.expectedIdsSortedBy(SortOrder.DESC) { it.enabled }
                }

                then("a field without an order should be sorted in ascending order") {
                    fetchPage(sortedBy = HttpMonitorSortField.URL).result.map { it.id } shouldBe
                        monitors.expectedIdsSortedBy(SortOrder.ASC) { it.url.toString().lowercase() }
                }

                then("an order without a field should sort by the name") {
                    fetchPage(sortOrder = SortOrder.DESC).result.map { it.id } shouldBe
                        monitors.expectedIdsSortedBy(SortOrder.DESC) { it.name.lowercase() }
                }
            }

            `when`("the monitors are filtered by their name") {
                listOf("Prod API", "prod-web", "staging", "100%_up", "1000xup").forEach {
                    createHttpMonitor(monitorRepository, monitorName = it)
                }

                then("it should match a part of the name, without regard to the case of the letters") {
                    val page = fetchPage(name = " PROD ")

                    page.result.map { it.name } shouldContainExactly listOf("Prod API", "prod-web")
                    page.totalCount shouldBe 2
                }

                then("the wildcards of LIKE should be matched literally") {
                    fetchPage(name = "%").result.map { it.name } shouldContainExactly listOf("100%_up")
                    fetchPage(name = "_").result.map { it.name } shouldContainExactly listOf("100%_up")
                }

                then("a blank name should not filter anything") {
                    fetchPage(name = "  ").totalCount shouldBe 5
                }
            }

            `when`("the other filters are combined with the paging") {
                val shop = createHttpMonitor(monitorRepository, monitorName = "shop-1", category = "shop")
                createSSLEventRecord(
                    dslContext,
                    monitorId = shop.id,
                    status = SslStatus.VALID,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                val shop2 = createHttpMonitor(monitorRepository, monitorName = "shop-2", category = "shop")
                createSSLEventRecord(
                    dslContext,
                    monitorId = shop2.id,
                    status = SslStatus.VALID,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                createHttpMonitor(monitorRepository, monitorName = "shop-3", category = "shop", enabled = false)
                val blog = createHttpMonitor(monitorRepository, monitorName = "blog", category = "blog")
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = blog.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                val noSsl = createHttpMonitor(monitorRepository, monitorName = "no-category", sslCheckEnabled = false)
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = noSsl.id,
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
                    fetchPage(category = "").result.map { it.name } shouldContainExactly listOf("no-category")
                }

                then("the status filters should be applied too") {
                    fetchPage(uptimeStatus = listOf(UptimeStatus.DOWN)).result.map { it.id } shouldBe
                        listOf(blog.id, noSsl.id)
                    fetchPage(sslCheckEnabled = false).result.map { it.id } shouldBe listOf(noSsl.id)
                    fetchPage(sslStatus = listOf(SslStatus.VALID)).result.map { it.id } shouldBe
                        listOf(shop.id, shop2.id)
                    fetchPage(sslStatus = listOf(SslStatus.INVALID)).result.shouldBeEmpty()
                }

                // A full page can't tell the total itself, so these run the count query with the joins of the filters
                then("the totals of a full page should count with the status filters too") {
                    fetchPage(uptimeStatus = listOf(UptimeStatus.DOWN), size = 1).totalCount shouldBe 2
                    fetchPage(sslStatus = listOf(SslStatus.VALID), size = 1).totalCount shouldBe 2
                    fetchPage(sslStatus = listOf(SslStatus.VALID), uptimeStatus = listOf(UptimeStatus.DOWN), size = 1)
                        .totalCount shouldBe 0
                }
            }

            `when`("only some of the monitors are affected by a maintenance window") {
                val affected = createHttpMonitor(monitorRepository)
                val notAffected = createHttpMonitor(monitorRepository)
                createMaintenanceWindow(dslContext, monitors = listOf(MonitorID(MonitorType.HTTP_SSL, affected.name)))

                val page = fetchPage().result

                then("each monitor should get its own details, the same as the v2 endpoint returns") {
                    page.single { it.id == affected.id }.maintenanceWindows.shouldNotBeEmpty()
                    page.single { it.id == notAffected.id }.maintenanceWindows.shouldBeEmpty()
                    page.forEach { it.effectiveIntegrations.shouldNotBeEmpty() }
                    page.sortedBy { it.id } shouldBe v2MonitorClient.getMonitorsWithDetails(
                        enabled = null,
                        uptimeStatus = null,
                        sslStatus = null,
                        sslCheckEnabled = null,
                        category = null,
                    ).sortedBy { it.id }
                }
            }

            `when`("the uptime events of a monitor are fetched") {
                val monitor = createHttpMonitor(monitorRepository)
                val otherMonitor = createHttpMonitor(monitorRepository)
                // Every field puts these in a different order, see shouldTellTheSortFieldsApart below
                val now = getCurrentTimestamp()
                val seededIds = listOf(
                    seedUptimeEvent(
                        monitor.id,
                        now,
                        UptimeStatus.UP,
                        startedMinutesAgo = 10,
                        endedMinutesAgo = null,
                        updatedMinutesAgo = 1,
                    ),
                    seedUptimeEvent(
                        monitor.id,
                        now,
                        UptimeStatus.DOWN,
                        startedMinutesAgo = 50,
                        endedMinutesAgo = 20,
                        updatedMinutesAgo = 30,
                    ),
                    seedUptimeEvent(
                        monitor.id,
                        now,
                        UptimeStatus.UP,
                        startedMinutesAgo = 30,
                        endedMinutesAgo = 25,
                        updatedMinutesAgo = 26,
                    ),
                    seedUptimeEvent(
                        monitor.id,
                        now,
                        UptimeStatus.DOWN,
                        startedMinutesAgo = 40,
                        endedMinutesAgo = 35,
                        updatedMinutesAgo = 36,
                    ),
                    seedUptimeEvent(
                        monitor.id,
                        now,
                        UptimeStatus.UP,
                        startedMinutesAgo = 20,
                        endedMinutesAgo = 15,
                        updatedMinutesAgo = 5,
                    ),
                )
                seedUptimeEvent(
                    otherMonitor.id,
                    now,
                    UptimeStatus.DOWN,
                    startedMinutesAgo = 5,
                    endedMinutesAgo = null,
                    updatedMinutesAgo = 1,
                )

                val events = fetchUptimeEvents(monitor.id).result
                val keys = mapOf<EventSortField, (HttpUptimeEventDto) -> Comparable<*>?>(
                    EventSortField.ID to { it.id },
                    EventSortField.STATUS to { it.status },
                    EventSortField.STARTED_AT to { it.startedAt.toInstant() },
                    EventSortField.ENDED_AT to { it.endedAt?.toInstant() },
                    EventSortField.UPDATED_AT to { it.updatedAt.toInstant() },
                )

                then("it should return the events of the monitor, and only those") {
                    events.map { it.id } shouldContainExactlyInAnyOrder seededIds
                }

                then("every field should be covered by the test") {
                    keys.keys shouldBe EventSortField.entries.toSet()
                }

                then("the seeded events should tell every field apart") {
                    events.shouldTellTheSortFieldsApart(keys) { it.id }
                }

                then("the newest events should come first by default") {
                    events.map { it.id } shouldBe
                        events.expectedIdsSortedBy(SortOrder.DESC, { it.id }) { it.startedAt }
                }

                then("they should be sorted in both orders, the ones without a value last, the same values by ID") {
                    keys.forEach { (field, key) ->
                        SortOrder.entries.forEach { order ->
                            withClue("$field $order") {
                                val page = fetchUptimeEvents(monitor.id, sortedBy = field, sortOrder = order)

                                page.result.map { it.id } shouldBe events.expectedIdsSortedBy(order, { it.id }, key)
                            }
                        }
                    }
                }

                then("an order without a field should sort by the start of the events") {
                    fetchUptimeEvents(monitor.id, sortOrder = SortOrder.ASC).result.map { it.id } shouldBe
                        events.expectedIdsSortedBy(SortOrder.ASC, { it.id }) { it.startedAt }
                }

                then("it should return the requested page with the totals") {
                    val page = fetchUptimeEvents(monitor.id, page = 3, size = 2)

                    page.result shouldHaveSize 1
                    page.totalCount shouldBe 5
                    page.totalPages shouldBe 3
                }
            }

            `when`("the uptime events of a monitor that doesn't exist are fetched") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v3/http-monitors/12345678/uptime-events").awaitFirst()
                }

                then("it should return 404") {
                    exception.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("the SSL events of a monitor are fetched") {
                val monitor = createHttpMonitor(monitorRepository)
                val otherMonitor = createHttpMonitor(monitorRepository)
                // Every field puts these in a different order, see shouldTellTheSortFieldsApart below
                val now = getCurrentTimestamp()
                val seededIds = listOf(
                    seedSslEvent(
                        monitor.id,
                        now,
                        SslStatus.VALID,
                        startedMinutesAgo = 10,
                        endedMinutesAgo = null,
                        updatedMinutesAgo = 1,
                    ),
                    seedSslEvent(
                        monitor.id,
                        now,
                        SslStatus.INVALID,
                        startedMinutesAgo = 50,
                        endedMinutesAgo = 20,
                        updatedMinutesAgo = 30,
                    ),
                    seedSslEvent(
                        monitor.id,
                        now,
                        SslStatus.VALID,
                        startedMinutesAgo = 30,
                        endedMinutesAgo = 25,
                        updatedMinutesAgo = 26,
                    ),
                    seedSslEvent(
                        monitor.id,
                        now,
                        SslStatus.WILL_EXPIRE,
                        startedMinutesAgo = 40,
                        endedMinutesAgo = 35,
                        updatedMinutesAgo = 36,
                    ),
                    seedSslEvent(
                        monitor.id,
                        now,
                        SslStatus.INVALID,
                        startedMinutesAgo = 20,
                        endedMinutesAgo = 15,
                        updatedMinutesAgo = 5,
                    ),
                )
                seedSslEvent(
                    otherMonitor.id,
                    now,
                    SslStatus.INVALID,
                    startedMinutesAgo = 5,
                    endedMinutesAgo = null,
                    updatedMinutesAgo = 1,
                )

                val events = fetchSslEvents(monitor.id).result
                val keys = mapOf<EventSortField, (SSLEventDto) -> Comparable<*>?>(
                    EventSortField.ID to { it.id },
                    EventSortField.STATUS to { it.status },
                    EventSortField.STARTED_AT to { it.startedAt.toInstant() },
                    EventSortField.ENDED_AT to { it.endedAt?.toInstant() },
                    EventSortField.UPDATED_AT to { it.updatedAt.toInstant() },
                )

                then("it should return the events of the monitor, and only those") {
                    events.map { it.id } shouldContainExactlyInAnyOrder seededIds
                }

                then("every field should be covered by the test") {
                    keys.keys shouldBe EventSortField.entries.toSet()
                }

                then("the seeded events should tell every field apart") {
                    events.shouldTellTheSortFieldsApart(keys) { it.id }
                }

                then("the newest events should come first by default") {
                    events.map { it.id } shouldBe
                        events.expectedIdsSortedBy(SortOrder.DESC, { it.id }) { it.startedAt }
                }

                then("they should be sorted in both orders, the ones without a value last, the same values by ID") {
                    keys.forEach { (field, key) ->
                        SortOrder.entries.forEach { order ->
                            withClue("$field $order") {
                                val page = fetchSslEvents(monitor.id, sortedBy = field, sortOrder = order)

                                page.result.map { it.id } shouldBe events.expectedIdsSortedBy(order, { it.id }, key)
                            }
                        }
                    }
                }

                then("an order without a field should sort by the start of the events") {
                    fetchSslEvents(monitor.id, sortOrder = SortOrder.ASC).result.map { it.id } shouldBe
                        events.expectedIdsSortedBy(SortOrder.ASC, { it.id }) { it.startedAt }
                }

                then("it should return the requested page with the totals") {
                    val page = fetchSslEvents(monitor.id, page = 3, size = 2)

                    page.result shouldHaveSize 1
                    page.totalCount shouldBe 5
                    page.totalPages shouldBe 3
                }
            }

            `when`("the SSL events of a monitor that doesn't exist are fetched") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v3/http-monitors/12345678/ssl-events").awaitFirst()
                }

                then("it should return 404") {
                    exception.status shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("the parameters are invalid") {
                val invalidQueries = listOf("page=0", "size=0", "page=-1", "sortedBy=NOPE", "sortOrder=SIDEWAYS")

                then("it should return 400") {
                    invalidQueries.forEach { query ->
                        withClue(query) {
                            val exception = shouldThrow<HttpClientResponseException> {
                                client.exchange("/api/v3/http-monitors?$query").awaitFirst()
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
        url: String,
        uptimeCheckInterval: Int,
        uptime: SeededUptime?,
        // The status and the days until the certificate expires, if it's known
        ssl: Pair<SslStatus, Long?>?,
        createdMinutesAgo: Long,
        updatedMinutesAgo: Long,
    ): Long {
        val monitor = createHttpMonitor(
            monitorRepository,
            monitorName = name,
            enabled = enabled,
            url = url,
            uptimeCheckInterval = uptimeCheckInterval,
        )
        uptime?.let { seeded ->
            createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = seeded.status,
                startedAt = now.minusMinutes(seeded.startedMinutesAgo),
                updatedAt = now.minusMinutes(seeded.lastCheckMinutesAgo),
                endedAt = null,
            )
        }
        ssl?.let { (status, expiresInDays) ->
            createSSLEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = status,
                startedAt = now.minusMinutes(1),
                sslExpiryDate = expiresInDays?.let { now.plusDays(it) },
                endedAt = null,
            )
        }
        dslContext.setCreatedAndUpdatedAt(
            idField = HTTP_MONITOR.ID,
            createdAtField = HTTP_MONITOR.CREATED_AT,
            updatedAtField = HTTP_MONITOR.UPDATED_AT,
            monitorId = monitor.id,
            createdAt = now.minusMinutes(createdMinutesAgo),
            updatedAt = now.minusMinutes(updatedMinutesAgo),
        )
        return monitor.id
    }

    private fun seedUptimeEvent(
        monitorId: Long,
        now: OffsetDateTime,
        status: UptimeStatus,
        startedMinutesAgo: Long,
        endedMinutesAgo: Long?,
        updatedMinutesAgo: Long,
    ): Long = createHttpUptimeEventRecord(
        dslContext,
        monitorId = monitorId,
        status = status,
        startedAt = now.minusMinutes(startedMinutesAgo),
        endedAt = endedMinutesAgo?.let { now.minusMinutes(it) },
        updatedAt = now.minusMinutes(updatedMinutesAgo),
    ).id

    private fun fetchUptimeEvents(
        monitorId: Long,
        page: Int = 1,
        size: Int = 100,
        sortedBy: EventSortField? = null,
        sortOrder: SortOrder? = null,
    ) = monitorClient.getUptimeEventsPage(monitorId, page, size, sortedBy, sortOrder)

    private fun seedSslEvent(
        monitorId: Long,
        now: OffsetDateTime,
        status: SslStatus,
        startedMinutesAgo: Long,
        endedMinutesAgo: Long?,
        updatedMinutesAgo: Long,
    ): Long = createSSLEventRecord(
        dslContext,
        monitorId = monitorId,
        status = status,
        startedAt = now.minusMinutes(startedMinutesAgo),
        endedAt = endedMinutesAgo?.let { now.minusMinutes(it) },
        updatedAt = now.minusMinutes(updatedMinutesAgo),
    ).id

    private fun fetchSslEvents(
        monitorId: Long,
        page: Int = 1,
        size: Int = 100,
        sortedBy: EventSortField? = null,
        sortOrder: SortOrder? = null,
    ) = monitorClient.getSSLEventsPage(monitorId, page, size, sortedBy, sortOrder)

    private fun fetchPage(
        enabled: Boolean? = null,
        uptimeStatus: List<UptimeStatus>? = null,
        sslStatus: List<SslStatus>? = null,
        sslCheckEnabled: Boolean? = null,
        category: String? = null,
        name: String? = null,
        page: Int = 1,
        size: Int = 100,
        sortedBy: HttpMonitorSortField? = null,
        sortOrder: SortOrder? = null,
    ) = monitorClient.getMonitorsPage(
        enabled = enabled,
        uptimeStatus = uptimeStatus,
        sslStatus = sslStatus,
        sslCheckEnabled = sslCheckEnabled,
        category = category,
        name = name,
        page = page,
        size = size,
        sortedBy = sortedBy,
        sortOrder = sortOrder,
    )
}
