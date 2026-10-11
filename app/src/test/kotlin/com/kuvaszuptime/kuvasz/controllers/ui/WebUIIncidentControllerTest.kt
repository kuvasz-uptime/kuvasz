package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpUptimeEventRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import kotlinx.coroutines.reactive.awaitFirst

/**
 * The HTTP contract of the incident pages of the web UI: the binding of the query and path parameters, and the
 * statuses telling htmx what to do. What the pages render is covered by the UI tests.
 */
@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class WebUIIncidentControllerTest(
    @Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        suspend fun statusOf(uri: String): Int =
            try {
                client.exchange(HttpRequest.GET<Any>(uri), String::class.java).awaitFirst().code()
            } catch (ex: HttpClientResponseException) {
                ex.response.code()
            }

        given("the incident list") {

            `when`("the type filter is unknown") {

                then("a 400 is returned, both for the page and for its list") {
                    statusOf("/incidents?type=foo") shouldBe HttpStatus.BAD_REQUEST.code
                    statusOf("/incidents/fragments/list?type=foo") shouldBe HttpStatus.BAD_REQUEST.code
                }
            }

            `when`("the type is lowercase or uppercase") {

                then("both of them are bound") {
                    statusOf("/incidents?type=docker") shouldBe HttpStatus.OK.code
                    statusOf("/incidents/fragments/list?type=DOCKER") shouldBe HttpStatus.OK.code
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
    }
}
