package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.mocks.createIncidentNote
import com.kuvaszuptime.kuvasz.mocks.createSSLEventRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.UIDefaults
import com.kuvaszuptime.kuvasz.util.formatAsSimpleInterval
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst
import java.time.Duration

/**
 * The incident pages of the web UI, requested over the wire the same way as a browser does, so the binding of the
 * query and path parameters is covered too.
 */
@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class WebUIIncidentControllerTest(
    @Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        suspend fun get(uri: String): String = client.retrieve(HttpRequest.GET<Any>(uri)).awaitFirst()

        suspend fun statusOf(uri: String): HttpStatus =
            shouldThrow<HttpClientResponseException> { get(uri) }.status

        given("the incident list") {

            fun createIncidents() {
                val now = getCurrentTimestamp()
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository, monitorName = "http-monitor").id,
                    status = UptimeStatus.DOWN,
                    startedAt = now,
                    endedAt = null,
                )
                createDockerUptimeEventRecord(
                    dslContext,
                    monitorId = createDockerMonitor(dockerMonitorRepository, monitorName = "docker-monitor").id,
                    status = UptimeStatus.DOWN,
                    startedAt = now,
                    endedAt = null,
                )
            }

            `when`("the page is requested without filters") {
                val html = get("/incidents")

                then("the list is loaded from the fragment with the default period, and no period is in the title") {
                    val defaultPeriod = Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS)
                    html shouldContain """hx-get="/incidents/fragments/list?period=$defaultPeriod""""
                    html shouldContain """<option value="" selected="selected">"""
                    html shouldContain """data-testid="incident-list-refresh-button""""
                    html shouldNotContain Messages.lastX(defaultPeriod.formatAsSimpleInterval())
                }
            }

            `when`("the page is filtered by a type and a period") {
                val html = get("/incidents?type=docker&period=P1D")

                then("both filters keep their values, and the list is loaded with them") {
                    html shouldContain """<option value="docker" selected="selected">"""
                    html shouldContain """<option value="PT24H" selected="selected">"""
                    html shouldContain """hx-get="/incidents/fragments/list?period=PT24H&amp;type=docker""""
                }
            }

            `when`("the list is filtered by a type") {
                createIncidents()

                val html = get("/incidents/fragments/list?type=docker&period=P1D")

                then("only the incidents of that type are listed, and the toolbar's count is swapped out of band") {
                    html shouldContain ">docker-monitor<"
                    html shouldNotContain ">http-monitor<"
                    html shouldContain """data-testid="incident-count" hx-swap-oob="outerHTML""""
                    html shouldContain ">${Messages.incidentListCount(1)}<"
                }
            }

            `when`("the list is empty") {
                val html = get("/incidents/fragments/list")

                then("the empty state is shown with a zero count") {
                    html shouldContain """data-testid="empty-state""""
                    html shouldContain ">${Messages.incidentListCount(0)}<"
                }
            }

            `when`("the type filter is unknown") {

                then("a 400 is returned") {
                    statusOf("/incidents?type=foo") shouldBe HttpStatus.BAD_REQUEST
                    statusOf("/incidents/fragments/list?type=foo") shouldBe HttpStatus.BAD_REQUEST
                }
            }

            `when`("an incident is listed") {
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )

                val html = get("/incidents/fragments/list")

                then("its row links to its details page") {
                    html shouldContain """href="/incidents/http/${event.id}""""
                }
            }
        }

        given("the details page of an incident") {

            `when`("the incident is resolved, and it has notes") {
                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(httpMonitorRepository, monitorName = "my-monitor")
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = now,
                    error = "Connection refused",
                    integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now,
                    endedAt = null,
                    integrations = arrayOf(IntegrationID(IntegrationType.PAGERDUTY, "on-call")),
                )
                createIncidentNote(
                    dslContext,
                    IncidentType.HTTP,
                    event.id,
                    status = InvestigationStatus.IDENTIFIED,
                    content = "The <b>database</b> is down",
                    public = true,
                )

                val html = get("/incidents/HTTP/${event.id}")

                then("it's not refreshed, as a resolved incident never changes again") {
                    html shouldNotContain "/incidents/fragments/details/"
                }

                then("it tells the story of the incident on a timeline") {
                    html shouldContain ">my-monitor<"
                    html shouldContain "Connection refused"
                    html shouldContain "slack:ops"
                    html shouldContain "pagerduty:on-call"
                    html shouldContain ">${Messages.investigationStatusIdentified()}<"
                    html shouldContain ">${Messages.incidentResolvedEvent()}<"
                    html shouldContain """data-testid="incident-resolution-integrations-event""""
                }

                then("the content of the notes is escaped") {
                    html shouldContain "The &lt;b&gt;database&lt;/b&gt; is down"
                    html shouldNotContain "<b>database</b>"
                }

                then("the notes can be added and edited, including whether they are public") {
                    html shouldContain """data-testid="add-incident-note-button""""
                    html shouldContain """data-testid="incident-note-edit-button""""
                    html shouldContain """data-testid="incident-note-modal""""
                    html shouldContain """data-testid="incident-note-visibility""""
                    html shouldContain Messages.notePublicLabel()
                }
            }

            `when`("the notes are timed before the start, at the start, during, and after the resolution") {
                val now = getCurrentTimestamp()
                val startedAt = now.minusHours(2)
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = startedAt,
                    endedAt = now.minusHours(1),
                    integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now.minusHours(1),
                    endedAt = null,
                    integrations = arrayOf(IntegrationID(IntegrationType.SLACK, "ops")),
                )
                // Created in a different order than their times, to prove that the times decide
                createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "After", createdAt = now)
                createIncidentNote(
                    dslContext,
                    IncidentType.HTTP,
                    event.id,
                    content = "Before",
                    timestamp = startedAt.minusMinutes(10),
                )
                createIncidentNote(
                    dslContext,
                    IncidentType.HTTP,
                    event.id,
                    content = "During",
                    timestamp = startedAt.plusMinutes(30),
                )
                createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "AtStart", timestamp = startedAt)

                val html = get("/incidents/http/${event.id}")

                then("the timeline is chronological, with the incident's own events first at the same time") {
                    val eventNames = "started|integrations|note|resolved|resolution-integrations"
                    val events = Regex("data-testid=\"(incident-(?:$eventNames)-event)\"")
                        .findAll(html).map { it.groupValues[1] }.toList()
                    val notes = Regex("data-testid=\"incident-note-content\">([^<]*)<")
                        .findAll(html).map { it.groupValues[1] }.toList()

                    events shouldBe listOf(
                        "incident-note-event",
                        "incident-started-event",
                        "incident-integrations-event",
                        "incident-note-event",
                        "incident-note-event",
                        "incident-resolved-event",
                        "incident-resolution-integrations-event",
                        "incident-note-event",
                    )
                    notes shouldBe listOf("Before", "AtStart", "During", "After")
                }
            }

            `when`("no integrations were notified about the incident") {
                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = now,
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now,
                    endedAt = null,
                )

                val html = get("/incidents/http/${event.id}")

                then("the timeline has nothing about the integrations") {
                    html shouldContain """data-testid="incident-started-event""""
                    html shouldContain """data-testid="incident-resolved-event""""
                    html shouldNotContain """data-testid="incident-integrations-event""""
                    html shouldNotContain """data-testid="incident-resolution-integrations-event""""
                }
            }

            `when`("the incident is an SSL incident") {
                val event = createSSLEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository).id,
                    status = SslStatus.INVALID,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                createIncidentNote(dslContext, IncidentType.SSL, event.id)

                val html = get("/incidents/ssl/${event.id}")

                then("its notes can't be public, as SSL incidents are never shown on the status pages") {
                    html shouldContain """data-testid="incident-note-event""""
                    html shouldNotContain """data-testid="incident-note-visibility""""
                    html shouldNotContain Messages.notePublicLabel()
                }

                then("it's still ongoing, so there is no resolution on the timeline, and it's refreshed") {
                    html shouldNotContain """data-testid="incident-resolved-event""""
                    html shouldContain """hx-get="/incidents/fragments/details/ssl/${event.id}""""
                }
            }

            `when`("the incident doesn't exist") {
                val upEvent = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository).id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )

                then("a 404 is returned, for a missing event, and an event that is not an incident") {
                    statusOf("/incidents/http/${upEvent.id + 1}") shouldBe HttpStatus.NOT_FOUND
                    statusOf("/incidents/http/${upEvent.id}") shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        given("the refreshed details of an incident") {

            suspend fun exchange(uri: String): HttpResponse<String> =
                client.exchange(HttpRequest.GET<Any>(uri), String::class.java).awaitFirst()

            `when`("the incident is ongoing") {
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository, monitorName = "heading-monitor").id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "Still looking")

                val response = exchange("/incidents/fragments/details/http/${event.id}")
                val html = response.body().shouldNotBeNull()

                then("the heading is rendered with the status of the incident, and it keeps polling") {
                    response.code() shouldBe HttpStatus.OK.code
                    html shouldContain ">heading-monitor<"
                    html shouldContain """data-testid="incident-status-indicator""""
                    html shouldContain "status-indicator-animated"
                    html shouldContain """hx-get="/incidents/fragments/details/http/${event.id}""""
                }

                then("the timeline comes along, swapped out of band") {
                    html shouldContain """id="incident-timeline" class="row row-cards" hx-swap-oob="""
                    html shouldContain "Still looking"
                    html shouldNotContain """data-testid="incident-resolved-event""""
                }
            }

            `when`("the incident has been resolved") {
                val now = getCurrentTimestamp()
                val monitor = createHttpMonitor(httpMonitorRepository)
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = now,
                )
                createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = monitor.id,
                    status = UptimeStatus.UP,
                    startedAt = now,
                    endedAt = null,
                    integrations = arrayOf(IntegrationID(IntegrationType.PAGERDUTY, "on-call")),
                )

                val response = exchange("/incidents/fragments/details/HTTP/${event.id}")
                val html = response.body().shouldNotBeNull()

                then("the resolution is rendered, and htmx is told to stop polling") {
                    response.code() shouldBe 286
                    html shouldNotContain "status-indicator-animated"
                    html shouldNotContain "/incidents/fragments/details/"
                    html shouldContain """hx-swap-oob="""
                    html shouldContain """data-testid="incident-resolved-event""""
                    html shouldContain """data-testid="incident-resolution-integrations-event""""
                    html shouldContain "pagerduty:on-call"
                }
            }

            `when`("the incident doesn't exist") {

                then("a 404 is returned") {
                    statusOf("/incidents/fragments/details/http/1234") shouldBe HttpStatus.NOT_FOUND
                }
            }

            `when`("the incident type is unknown") {

                then("a 400 is returned") {
                    statusOf("/incidents/foo/1234") shouldBe HttpStatus.BAD_REQUEST
                    statusOf("/incidents/fragments/details/foo/1234") shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }
}

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
@Property(name = "app-config.incident-notes-enabled", value = "false")
class WebUIDisabledIncidentNotesTest(
    @Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("the details page of an incident when incident notes are disabled") {

            `when`("the incident has notes") {
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository).id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )
                createIncidentNote(dslContext, IncidentType.HTTP, event.id, content = "Still visible")

                val html = client.retrieve(HttpRequest.GET<Any>("/incidents/http/${event.id}")).awaitFirst()

                then("the notes are shown, but they can't be added, edited or deleted") {
                    html shouldContain "Still visible"
                    html shouldNotContain """data-testid="add-incident-note-button""""
                    html shouldNotContain """data-testid="incident-note-edit-button""""
                    html shouldNotContain """data-testid="incident-note-modal""""
                }
            }
        }
    }
}
