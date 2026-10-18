package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.views.htmx.http.HtmxResponseHeaders
import kotlinx.coroutines.reactive.awaitFirst

/**
 * The HTTP contract of the incident pages of the web UI: the binding of the query and path parameters, and the
 * statuses and the headers telling htmx what to do. What the pages render is covered by the UI tests.
 */
@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class WebUIIncidentControllerTest(
    @Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        suspend fun get(uri: String): HttpResponse<String> =
            client.exchange(HttpRequest.GET(uri), String::class.java).awaitFirst()

        suspend fun replacedUrlOf(uri: String): String? = get(uri).header(HtmxResponseHeaders.HX_REPLACE_URL)

        suspend fun statusOf(uri: String): Int =
            try {
                client.exchange(HttpRequest.GET<Any>(uri), String::class.java).awaitFirst().code()
            } catch (ex: HttpClientResponseException) {
                ex.response.code()
            }

        given("the incident list") {

            `when`("the type filter is unknown, e.g. in a URL edited by hand") {

                then("it's ignored instead of failing the request") {
                    statusOf("/incidents?type=foo") shouldBe HttpStatus.OK.code
                    replacedUrlOf("/incidents/fragments/list?type=foo") shouldBe "/incidents"
                }
            }

            `when`("the type is lowercase or uppercase") {

                then("both of them are bound, and put into the URL in lowercase") {
                    statusOf("/incidents?type=docker") shouldBe HttpStatus.OK.code
                    replacedUrlOf("/incidents/fragments/list?type=DOCKER") shouldBe "/incidents?type=docker"
                }
            }

            `when`("every filter, the sorting and the page are given") {

                then("the fragment replaces the URL of the page with the one leaving out the defaults") {
                    val query = "name=+api+&type=http&period=P30D&includeResolved=false&monitorId=3" +
                        "&sortedBy=MONITOR_NAME&sortOrder=ASC&page=1&size=10"

                    replacedUrlOf("/incidents/fragments/list?$query") shouldBe "/incidents?name=api&type=http" +
                        "&period=PT720H&includeResolved=false&monitorId=3&sortedBy=MONITOR_NAME&size=10"
                }
            }

            `when`("the requested page is past the end of the list") {
                val monitorId = createHttpMonitor(httpMonitorRepository).id
                repeat(INCIDENT_COUNT) { index ->
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = monitorId,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusHours(index + 1L),
                        endedAt = getCurrentTimestamp().minusHours(index + 1L).plusMinutes(1),
                    )
                }

                then("its last page is shown, and put into the URL") {
                    replacedUrlOf("/incidents/fragments/list?size=10&page=9") shouldBe "/incidents?page=2&size=10"
                }
            }
        }

        given("the incident list on the details page of a monitor") {

            `when`("it's requested with a page") {
                val monitorId = createHttpMonitor(httpMonitorRepository).id
                repeat(INCIDENT_COUNT) { index ->
                    createHttpUptimeEventRecord(
                        dslContext,
                        monitorId = monitorId,
                        status = UptimeStatus.DOWN,
                        startedAt = getCurrentTimestamp().minusHours(index + 1L),
                        endedAt = getCurrentTimestamp().minusHours(index + 1L).plusMinutes(1),
                    )
                }
                val response = get("/incidents/fragments/monitor/http/$monitorId?size=10&page=2")

                then("the page is shown, without replacing the URL of the details page") {
                    response.status shouldBe HttpStatus.OK
                    response.header(HtmxResponseHeaders.HX_REPLACE_URL).shouldBeNull()
                    response.body() shouldContain """data-list-page="2""""
                }
            }

            `when`("the monitor doesn't exist") {

                then("it has no incidents to show") {
                    val response = get("/incidents/fragments/monitor/ssl/1234")

                    response.status shouldBe HttpStatus.OK
                    response.body() shouldContain """data-list-page="1""""
                }
            }

            `when`("the incident type is unknown") {

                then("a 400 is returned") {
                    statusOf("/incidents/fragments/monitor/foo/1234") shouldBe HttpStatus.BAD_REQUEST.code
                }
            }
        }

        given("the details of an incident") {

            `when`("the incident doesn't exist") {
                val upEvent = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository).id,
                    status = UptimeStatus.UP,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )

                then("a 404 is returned, for a missing event, and an event that is not an incident") {
                    statusOf("/incidents/http/${upEvent.id + 1}") shouldBe HttpStatus.NOT_FOUND.code
                    statusOf("/incidents/http/${upEvent.id}") shouldBe HttpStatus.NOT_FOUND.code
                    statusOf("/incidents/fragments/details/http/${upEvent.id}") shouldBe HttpStatus.NOT_FOUND.code
                }
            }

            `when`("the incident type is unknown") {

                then("a 400 is returned") {
                    statusOf("/incidents/foo/1234") shouldBe HttpStatus.BAD_REQUEST.code
                    statusOf("/incidents/fragments/details/foo/1234") shouldBe HttpStatus.BAD_REQUEST.code
                }
            }

            `when`("the refreshed details of an ongoing incident are requested") {
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository).id,
                    status = UptimeStatus.DOWN,
                    startedAt = getCurrentTimestamp(),
                    endedAt = null,
                )

                then("a 200 is returned, so htmx keeps polling") {
                    statusOf("/incidents/fragments/details/http/${event.id}") shouldBe HttpStatus.OK.code
                }
            }

            `when`("the refreshed details of a resolved incident are requested") {
                val now = getCurrentTimestamp()
                val event = createHttpUptimeEventRecord(
                    dslContext,
                    monitorId = createHttpMonitor(httpMonitorRepository).id,
                    status = UptimeStatus.DOWN,
                    startedAt = now.minusHours(1),
                    endedAt = now,
                )

                then("htmx is told to stop polling") {
                    statusOf("/incidents/fragments/details/HTTP/${event.id}") shouldBe HTMX_STOP_POLLING
                }
            }
        }
    }

    private companion object {
        const val HTMX_STOP_POLLING = 286
        const val INCIDENT_COUNT = 11
    }
}
