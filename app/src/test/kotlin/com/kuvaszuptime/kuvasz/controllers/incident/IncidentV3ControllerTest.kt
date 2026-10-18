package com.kuvaszuptime.kuvasz.controllers.incident

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.monitor.expectedOrder
import com.kuvaszuptime.kuvasz.controllers.monitor.shouldTellTheSortFieldsApart
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsUptimeEvent.DNS_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.HttpUptimeEvent.HTTP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpUptimeEvent.ICMP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.PushUptimeEvent.PUSH_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.SslEvent.SSL_EVENT
import com.kuvaszuptime.kuvasz.jooq.tables.TcpUptimeEvent.TCP_UPTIME_EVENT
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDnsUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpUptimeEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import org.jooq.Record
import org.jooq.TableField
import java.time.Duration

// Far above what the sequences of the event tables reach during the tests, so the IDs can be set explicitly
private const val EVENT_ID_BASE = 1_000_000L

@MicronautTest
class IncidentV3ControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val incidentClient: IncidentV3Client,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec() {

    // The identity of an incident: the ID of its event is only unique within its type
    private val identity: (IncidentDto) -> Pair<IncidentType, Long> = { it.incidentType to it.eventId }
    private val tieBreak = compareBy<IncidentDto>({ it.incidentType.name }, { it.eventId })

    init {
        given("the v3 getIncidentsPage() endpoint") {

            `when`("there are incidents of every kind") {
                val seeded = seedIncidents()

                val incidents = fetchIncidents().result
                val keys = mapOf<IncidentSortField, (IncidentDto) -> Comparable<*>?>(
                    IncidentSortField.ID to { it.eventId },
                    IncidentSortField.MONITOR_NAME to { it.monitorName.lowercase() },
                    IncidentSortField.INCIDENT_TYPE to { it.incidentType.name.lowercase() },
                    IncidentSortField.STATUS to { it.status.name.lowercase() },
                    IncidentSortField.STARTED_AT to { it.startedAt.toInstant() },
                    IncidentSortField.ENDED_AT to { it.endedAt?.toInstant() },
                    IncidentSortField.UPDATED_AT to { it.updatedAt.toInstant() },
                )

                then("it should return the incidents of the period, of the enabled monitors only") {
                    incidents.map(identity) shouldContainExactlyInAnyOrder seeded
                }

                then("every field should be covered by the test") {
                    keys.keys shouldBe IncidentSortField.entries.toSet()
                }

                then("the seeded incidents should tell every field apart") {
                    incidents.shouldTellTheSortFieldsApart(keys, tieBreak, identity)
                }

                then("the latest started ones should come first by default") {
                    incidents.map(identity) shouldBe
                        incidents.expectedOrder(SortOrder.DESC, { it.startedAt.toInstant() }, tieBreak, identity)
                }

                then("they should be sorted in both orders, the ones without a value last, the same values by ID") {
                    keys.forEach { (field, key) ->
                        SortOrder.entries.forEach { order ->
                            withClue("$field $order") {
                                val page = fetchIncidents(sortedBy = field, sortOrder = order)

                                page.result.map(identity) shouldBe
                                    incidents.expectedOrder(order, key, tieBreak, identity)
                            }
                        }
                    }
                }

                then("walking the pages should return the same order, without any incident twice or missing") {
                    val walked = (1..3).flatMap { fetchIncidents(page = it, size = 2).result.map(identity) }

                    walked shouldBe incidents.map(identity)
                    fetchIncidents(page = 3, size = 2).totalPages shouldBe 3
                }

                then("the filters should narrow the incidents and their totals") {
                    fetchIncidents(name = "BRA").result.map { it.monitorName }.toSet() shouldBe setOf("Bravo")
                    fetchIncidents(includeResolved = false).result.map { it.incidentType } shouldContainExactly
                        listOf(IncidentType.HTTP, IncidentType.PUSH)
                    fetchIncidents(period = Duration.ofMinutes(30)).totalCount shouldBe 4
                    fetchIncidents(period = Duration.ofDays(10)).totalCount shouldBe 7
                }
            }

            `when`("the parameters are invalid") {
                val invalidQueries = listOf("page=0", "size=0", "sortedBy=NOPE", "sortOrder=SIDEWAYS", "period=NOPE")

                then("it should return 400") {
                    invalidQueries.forEach { query ->
                        withClue(query) {
                            val exception = shouldThrow<HttpClientResponseException> {
                                client.exchange("/api/v3/incidents?$query").awaitFirst()
                            }
                            exception.status shouldBe HttpStatus.BAD_REQUEST
                        }
                    }
                }
            }
        }

        given("the v3 getIncidentsOfTypePage() endpoint") {

            `when`("there are incidents of every kind") {
                seedIncidents()

                then("it should return the incidents of the given type only, with their totals") {
                    val page = incidentClient.getIncidentsOfTypePage(
                        incidentType = IncidentType.SSL,
                        monitorId = null,
                        period = null,
                        includeResolved = null,
                        name = null,
                        page = 1,
                        size = 100,
                        sortedBy = null,
                        sortOrder = null,
                    )

                    page.result.map(identity) shouldBe listOf(IncidentType.SSL to EVENT_ID_BASE + 1)
                    page.totalCount shouldBe 1
                }

                then("the monitor filter should be applied within the type") {
                    val tcpMonitorId = fetchIncidents().result.single { it.incidentType == IncidentType.TCP }.monitorId

                    incidentClient.getIncidentsOfTypePage(
                        incidentType = IncidentType.TCP,
                        monitorId = tcpMonitorId,
                        period = null,
                        includeResolved = null,
                        name = "ALPHA",
                        page = 1,
                        size = 100,
                        sortedBy = IncidentSortField.STARTED_AT,
                        sortOrder = SortOrder.DESC,
                    ).result.map(identity) shouldBe listOf(IncidentType.TCP to EVENT_ID_BASE + 5)
                }

                then("the period and the resolved filters should be applied within the type") {
                    fun sslIncidents(period: Duration, includeResolved: Boolean) =
                        incidentClient.getIncidentsOfTypePage(
                            incidentType = IncidentType.SSL,
                            monitorId = null,
                            period = period,
                            includeResolved = includeResolved,
                            name = null,
                            page = 1,
                            size = 100,
                            sortedBy = null,
                            sortOrder = null,
                        ).totalCount

                    // The SSL incident was resolved 20 minutes ago
                    sslIncidents(period = Duration.ofMinutes(30), includeResolved = true) shouldBe 1
                    sslIncidents(period = Duration.ofMinutes(10), includeResolved = true) shouldBe 0
                    sslIncidents(period = Duration.ofMinutes(30), includeResolved = false) shouldBe 0
                }
            }

            `when`("there is no incident of the given type") {
                then("it should return an empty page") {
                    val page = incidentClient.getIncidentsOfTypePage(
                        incidentType = IncidentType.DOCKER,
                        monitorId = null,
                        period = null,
                        includeResolved = null,
                        name = null,
                        page = 1,
                        size = 100,
                        sortedBy = null,
                        sortOrder = null,
                    )

                    page.result.shouldBeEmpty()
                    page.totalCount shouldBe 0
                }
            }

            `when`("the type is unknown") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange("/api/v3/incidents/NOPE").awaitFirst()
                }

                then("it should return 400") {
                    exception.status shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }

    /**
     * Seeds six incidents of the default period that tell every sort field apart, one that is resolved too long ago,
     * and one of a disabled monitor, then returns the identities of the six.
     */
    private fun seedIncidents(): List<Pair<IncidentType, Long>> {
        val now = getCurrentTimestamp()
        val bravo = createHttpMonitor(httpMonitorRepository, monitorName = "Bravo")
        val alpha = createTcpMonitor(tcpMonitorRepository, monitorName = "alpha")
        val charlie = createIcmpMonitor(icmpMonitorRepository, monitorName = "charlie")
        val delta = createPushMonitor(pushMonitorRepository, monitorName = "Delta")
        val echo = createDnsMonitor(dnsMonitorRepository, monitorName = "echo")
        val disabled = createTcpMonitor(tcpMonitorRepository, monitorName = "disabled", enabled = false)

        createHttpUptimeEventRecord(
            dslContext,
            monitorId = bravo.id,
            status = UptimeStatus.DOWN,
            startedAt = now.minusMinutes(10),
            updatedAt = now.minusMinutes(1),
            endedAt = null,
        ).id.withEventId(HTTP_UPTIME_EVENT.ID, 3)
        createSSLEventRecord(
            dslContext,
            monitorId = bravo.id,
            status = SslStatus.INVALID,
            startedAt = now.minusMinutes(50),
            endedAt = now.minusMinutes(20),
            updatedAt = now.minusMinutes(22),
        ).id.withEventId(SSL_EVENT.ID, 1)
        createTcpUptimeEventRecord(
            dslContext,
            monitorId = alpha.id,
            status = UptimeStatus.DOWN,
            startedAt = now.minusMinutes(30),
            endedAt = now.minusMinutes(25),
        ).id.withEventId(TCP_UPTIME_EVENT.ID, 5)
        createIcmpUptimeEventRecord(
            dslContext,
            monitorId = charlie.id,
            status = UptimeStatus.DOWN,
            startedAt = now.minusMinutes(40),
            endedAt = now.minusMinutes(35),
            updatedAt = now.minusMinutes(36),
        ).id.withEventId(ICMP_UPTIME_EVENT.ID, 2)
        createPushUptimeEventRecord(
            dslContext,
            monitorId = delta.id,
            status = UptimeStatus.DOWN,
            startedAt = now.minusMinutes(20),
            updatedAt = now.minusMinutes(5),
            endedAt = null,
        ).id.withEventId(PUSH_UPTIME_EVENT.ID, 4)
        // The same event ID as the HTTP one, so they are told apart by their type
        createDnsUptimeEventRecord(
            dslContext,
            monitorId = echo.id,
            status = UptimeStatus.DOWN,
            startedAt = now.minusMinutes(60),
            endedAt = now.minusMinutes(45),
            updatedAt = now.minusMinutes(44),
        ).id.withEventId(DNS_UPTIME_EVENT.ID, 3)
        // Resolved before the default period of 7 days
        createTcpUptimeEventRecord(
            dslContext,
            monitorId = alpha.id,
            status = UptimeStatus.DOWN,
            startedAt = now.minusDays(9),
            endedAt = now.minusDays(8),
        ).id.withEventId(TCP_UPTIME_EVENT.ID, 6)
        createTcpUptimeEventRecord(
            dslContext,
            monitorId = disabled.id,
            status = UptimeStatus.DOWN,
            startedAt = now.minusMinutes(10),
            endedAt = null,
        )

        return listOf(
            IncidentType.HTTP to EVENT_ID_BASE + 3,
            IncidentType.SSL to EVENT_ID_BASE + 1,
            IncidentType.TCP to EVENT_ID_BASE + 5,
            IncidentType.ICMP to EVENT_ID_BASE + 2,
            IncidentType.PUSH to EVENT_ID_BASE + 4,
            IncidentType.DNS to EVENT_ID_BASE + 3,
        )
    }

    private fun <R : Record> Long.withEventId(idField: TableField<R, Long>, offset: Long) {
        dslContext.update(checkNotNull(idField.table))
            .set(idField, EVENT_ID_BASE + offset)
            .where(idField.eq(this))
            .execute()
    }

    private fun fetchIncidents(
        includeResolved: Boolean? = null,
        period: Duration? = null,
        name: String? = null,
        page: Int = 1,
        size: Int = 100,
        sortedBy: IncidentSortField? = null,
        sortOrder: SortOrder? = null,
    ) = incidentClient.getIncidentsPage(
        monitorId = null,
        period = period,
        includeResolved = includeResolved,
        name = name,
        page = page,
        size = size,
        sortedBy = sortedBy,
        sortOrder = sortOrder,
    )
}
