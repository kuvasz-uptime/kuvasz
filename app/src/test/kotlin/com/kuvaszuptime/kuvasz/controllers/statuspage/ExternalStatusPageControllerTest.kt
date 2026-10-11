package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.StatusPageNotFoundException
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.reactive.awaitFirst
import java.time.OffsetDateTime

/**
 * These tests are focusing on the internal behavior of the controller WITHOUT TAKING AUTHENTICATION INTO ACCOUNT.
 * The tests that take the authentication into account are located in
 * [com.kuvaszuptime.kuvasz.security.AuthenticationTest] and
 * [com.kuvaszuptime.kuvasz.security.DisabledAuthenticationTest]
 */
@MicronautTest
class ExternalStatusPageControllerTest(
    @param:Client("/") private val client: HttpClient,
    private val statusPageActions: StatusPageActions,
    private val statusPageDataActions: StatusPageDataActions,
) : DatabaseBehaviorSpec({

    given("the getDefaultStatusPage() endpoint") {

        `when`("it is called") {

            then("it should return 200 and fetch the data from the underlying service") {
                val mockDataService = getMock(statusPageDataActions)
                every { mockDataService.getCachedDefaultStatusPageData() } returns mockk(relaxed = true)

                val response = client.exchange("/status").awaitFirst()

                response.status shouldBe HttpStatus.OK
                verify(exactly = 1) { mockDataService.getCachedDefaultStatusPageData() }
            }
        }
    }

    given("the getStatusPage(slug) endpoint") {

        val testSlug = "status-page-1"

        `when`("the status page with the given slug exists") {

            then("it should return 200 and fetch the data from the underlying service") {
                val mockService = getMock(statusPageActions)
                val mockDataService = getMock(statusPageDataActions)
                every { mockService.getStatusPageBySlug(testSlug, null) } returns mockk {
                    every { id } returns 123L
                }
                every { mockDataService.getCachedStatusPageData(123) } returns mockk(relaxed = true)

                val response = client.exchange("/status/$testSlug").awaitFirst()

                response.status shouldBe HttpStatus.OK
                verify(exactly = 1) { mockService.getStatusPageBySlug(testSlug, null) }
                verify(exactly = 1) { mockDataService.getCachedStatusPageData(123) }
            }
        }

        `when`("the status page with the given slug does not exist") {

            then("it should return 404 Not Found") {
                val mockService = getMock(statusPageActions)
                val mockDataService = getMock(statusPageDataActions)
                every { mockService.getStatusPageBySlug(testSlug, null) } throws StatusPageNotFoundException(testSlug)

                val ex = shouldThrow<HttpClientResponseException> {
                    client.exchange("/status/$testSlug").awaitFirst()
                }
                ex.status shouldBe HttpStatus.NOT_FOUND
                verify(exactly = 1) { mockService.getStatusPageBySlug(testSlug, null) }
                verify(exactly = 0) { mockDataService.getCachedStatusPageData(any()) }
            }
        }
    }
}) {
    @MockBean(StatusPageActions::class)
    fun mockStatusPageActions(): StatusPageActions = mockk()

    @MockBean(StatusPageDataActions::class)
    fun mockStatusPageDataActions(): StatusPageDataActions = mockk()
}

/**
 * The incidents on the rendered status pages, from the real data, so it's proven that nothing internal reaches the
 * visitors.
 */
@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class ExternalStatusPageIncidentsTest(
    @param:Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {
    init {

        fun get(uri: String, hxRequest: Boolean = false): HttpResponse<String> {
            val request = HttpRequest.GET<Any>(uri).apply { if (hxRequest) header("HX-Request", "true") }
            return client.toBlocking().exchange(request, String::class.java)
        }

        // A page with a single monitor, which has the given incident
        fun pageWithIncident(
            startedAt: OffsetDateTime,
            endedAt: OffsetDateTime?,
            monitorName: String = "Payments API",
        ): Pair<String, Long> {
            val monitor = createHttpMonitor(httpMonitorRepository, monitorName = monitorName)
            val incident = createHttpUptimeEventRecord(
                dslContext,
                monitorId = monitor.id,
                status = UptimeStatus.DOWN,
                startedAt = startedAt,
                endedAt = endedAt,
                updatedAt = endedAt ?: getCurrentTimestamp(),
                error = "Connection refused by 10.0.0.12",
            )
            endedAt?.let { upSince ->
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = upSince,
                    endedAt = null,
                    updatedAt = getCurrentTimestamp(),
                )
            }
            val page = createStatusPage(
                dslContext,
                public = true,
                monitors = listOf(MonitorID(MonitorType.HTTP_SSL, monitor.name)),
            )
            return page.slug to incident.id
        }

        given("a status page with an ongoing incident") {

            `when`("the incident has public and private notes") {
                val now = getCurrentTimestamp()
                val (slug, incidentId) = pageWithIncident(startedAt = now.minusMinutes(30), endedAt = null)
                createIncidentNote(
                    dslContext,
                    IncidentType.HTTP,
                    incidentId,
                    content = "We are looking into it",
                    public = true,
                    createdAt = now.minusMinutes(20),
                )
                createIncidentNote(dslContext, IncidentType.HTTP, incidentId, content = "The VPN is flaky again")

                val html = get("/status/$slug").body().shouldNotBeNull()

                then("the active incidents are open, with the public notes only, and nothing internal") {
                    html shouldContain """class="accordion-collapse collapse show" data-incidents-accordion="active""""
                    html shouldNotContain """data-testid="status-page-resolved-incidents""""
                    html shouldContain "We are looking into it"
                    html shouldNotContain "The VPN is flaky again"
                    html shouldNotContain "10.0.0.12"
                }

                then("the monitor's card counts the incident") {
                    html shouldContain """data-testid="monitor-incidents-badge""""
                    html shouldContain """data-testid="status-page-incidents-modal""""
                }
            }
        }

        given("a status page with an incident resolved within the last day") {

            `when`("it's rendered") {
                val now = getCurrentTimestamp()
                val (slug, _) = pageWithIncident(startedAt = now.minusHours(3), endedAt = now.minusHours(2))

                val html = get("/status/$slug").body().shouldNotBeNull()

                then("only the resolved incidents are there, closed") {
                    html shouldContain """class="accordion-collapse collapse" data-incidents-accordion="resolved""""
                    html shouldNotContain """data-testid="status-page-active-incidents""""
                }
            }
        }

        given("a status page with an ongoing and a recently resolved incident of different monitors") {

            `when`("it's rendered") {
                val now = getCurrentTimestamp()
                val resolved = createHttpMonitor(httpMonitorRepository, monitorName = "Alpha API")
                val ongoing = createHttpMonitor(httpMonitorRepository, monitorName = "Zulu API")
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = resolved.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(2),
                    endedAt = now.minusHours(1),
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = ongoing.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusMinutes(10),
                    endedAt = null,
                    updatedAt = now,
                )
                val page = createStatusPage(
                    dslContext,
                    public = true,
                    monitors = listOf(resolved, ongoing).map { MonitorID(MonitorType.HTTP_SSL, it.name) },
                )

                val html = get("/status/${page.slug}").body().shouldNotBeNull()

                then("the active incidents come first, whatever the names of their monitors are") {
                    html.indexOf("data-testid=\"status-page-active-incidents\"") shouldBeLessThan
                        html.indexOf("data-testid=\"status-page-resolved-incidents\"")
                    val alertHeadings = Regex("class=\"alert-heading\">([^<]*)<").findAll(html)
                    alertHeadings.map { it.groupValues[1] }.toList() shouldBe listOf("Zulu API", "Alpha API")
                }
            }
        }

        given("a status page with an incident resolved more than a day ago") {

            `when`("it's rendered") {
                val now = getCurrentTimestamp()
                val (slug, _) = pageWithIncident(startedAt = now.minusDays(3), endedAt = now.minusDays(2))

                val html = get("/status/$slug").body().shouldNotBeNull()

                then("there is no incidents accordion, but the incident is still counted on the monitor's card") {
                    html shouldNotContain """data-testid="status-page-active-incidents""""
                    html shouldNotContain """data-testid="status-page-resolved-incidents""""
                    html shouldContain """data-testid="monitor-incidents-badge""""
                }
            }
        }

        given("the public timeline of a resolved incident") {

            `when`("it has notes before, around, and after its resolution") {
                val now = getCurrentTimestamp()
                val startedAt = now.minusHours(3)
                val endedAt = now.minusHours(1)
                val (slug, incidentId) = pageWithIncident(startedAt = startedAt, endedAt = endedAt)
                fun note(content: String, status: InvestigationStatus, timestamp: OffsetDateTime) = createIncidentNote(
                    dslContext,
                    IncidentType.HTTP,
                    incidentId,
                    status = status,
                    content = content,
                    timestamp = timestamp,
                    public = true,
                )
                note("Investigating", InvestigationStatus.INVESTIGATING, startedAt.plusMinutes(5))
                note("Identified", InvestigationStatus.IDENTIFIED, startedAt.plusMinutes(30))
                note("Fixed it", InvestigationStatus.RESOLVED, endedAt.minusMinutes(1))
                note("Post-mortem", InvestigationStatus.RESOLVED, endedAt.plusMinutes(10))

                val html = get("/status/$slug").body().shouldNotBeNull()

                then("the nodes are the latest first, the RESOLVED neighbours of the resolution shown as part of it") {
                    val timeline = html.substringAfter("data-testid=\"status-page-resolved-incidents\"")
                        .substringBefore("data-testid=\"status-monitor-card\"")
                    val nodes = Regex("data-testid=\"(public-incident-(?:started|note|resolved))\"")
                        .findAll(timeline).map { it.groupValues[1] }.toList()
                    val contents = Regex("data-testid=\"public-incident-note-content\">([^<]*)<")
                        .findAll(timeline).map { it.groupValues[1] }.toList()

                    nodes shouldBe listOf(
                        "public-incident-resolved",
                        "public-incident-note",
                        "public-incident-note",
                        "public-incident-started",
                    )
                    // The resolution carries its neighbours in their order, then the standalone notes come
                    contents shouldBe listOf("Fixed it", "Post-mortem", "Identified", "Investigating")
                }
            }
        }

        given("the status page requested by itself (via htmx)") {

            `when`("it's refreshing itself") {
                val (slug, _) = pageWithIncident(startedAt = getCurrentTimestamp().minusMinutes(5), endedAt = null)

                val fullPage = get("/status/$slug")
                val fragment = get("/status/$slug", hxRequest = true)

                then("only its refreshed part is returned, and both responses vary by the htmx header") {
                    fullPage.body().shouldNotBeNull() shouldContain "<html"
                    fragment.body().shouldNotBeNull().let { body ->
                        body shouldNotContain "<html"
                        body shouldStartWith "<div id=\"status-page-content\">"
                        body shouldContain "Payments API"
                    }
                    fullPage.header(HttpHeaders.VARY) shouldBe "HX-Request"
                    fragment.header(HttpHeaders.VARY) shouldBe "HX-Request"
                }
            }
        }
    }
}
