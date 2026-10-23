package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createDockerMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.micronaut.views.htmx.http.HtmxResponseHeaders
import kotlinx.coroutines.reactive.awaitFirst

/**
 * The HTTP contract of the monitor list pages of the web UI: the binding of the query parameters of the lists, and the
 * URL their fragments give htmx to replace the one of the page with. What the lists render is covered by the UI tests.
 */
@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class WebUIMonitorListControllerTest(
    @Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        suspend fun get(uri: String): HttpResponse<String> =
            client.exchange(HttpRequest.GET<Any>(uri), String::class.java).awaitFirst()

        // Every type, by the path of its list, with a way to seed one of its monitors
        val seeders: Map<String, (name: String) -> Unit> = mapOf(
            "/http-monitors" to { name -> createHttpMonitor(httpMonitorRepository, monitorName = name) },
            "/push-monitors" to { name -> createPushMonitor(pushMonitorRepository, monitorName = name) },
            "/icmp-monitors" to { name -> createIcmpMonitor(icmpMonitorRepository, monitorName = name) },
            "/tcp-monitors" to { name -> createTcpMonitor(tcpMonitorRepository, monitorName = name) },
            "/dns-monitors" to { name -> createDnsMonitor(dnsMonitorRepository, monitorName = name) },
            "/docker-monitors" to { name -> createDockerMonitor(dockerMonitorRepository, monitorName = name) },
        )

        given("the monitor lists") {

            `when`("the query parameters are valid") {

                then("the fragment replaces the URL of the page with the one leaving out the defaults") {
                    seeders.keys.forEach { listPath ->
                        withClue(listPath) {
                            val query = "name=+api+&category=&enabled=false&uptimeStatus=DOWN&uptimeStatus=UP" +
                                "&sortedBy=NAME&sortOrder=DESC&page=1&size=25"
                            get("$listPath?$query").status shouldBe HttpStatus.OK

                            val fragment = get("$listPath/fragments/list?$query")

                            fragment.status shouldBe HttpStatus.OK
                            fragment.header(HtmxResponseHeaders.HX_REPLACE_URL) shouldBe
                                "$listPath?name=api&category=&enabled=false&uptimeStatus=UP&uptimeStatus=DOWN" +
                                "&sortedBy=NAME&sortOrder=DESC"
                        }
                    }
                }
            }

            `when`("the query parameters are unknown or out of range, e.g. in a URL edited by hand") {

                then("they are ignored instead of failing the request") {
                    seeders.keys.forEach { listPath ->
                        withClue(listPath) {
                            val query = "page=0&size=7&sortedBy=FOO&sortOrder=BAR&enabled=maybe&uptimeStatus=SIDEWAYS"
                            get("$listPath?$query").status shouldBe HttpStatus.OK
                            get("$listPath/fragments/list?$query").header(HtmxResponseHeaders.HX_REPLACE_URL) shouldBe
                                listPath
                        }
                    }
                }
            }

            `when`("the requested page is past the end of a list") {

                then("its last page is shown, and put into the URL") {
                    seeders.forEach { (listPath, seed) ->
                        withClue(listPath) {
                            (1..11).forEach { seed("$listPath-$it") }

                            val fragment = get("$listPath/fragments/list?size=10&page=5")

                            fragment.header(HtmxResponseHeaders.HX_REPLACE_URL) shouldBe "$listPath?page=2&size=10"
                            fragment.body() shouldContain """data-list-page="2""""
                        }
                    }
                }
            }

            `when`("a list is empty") {

                then("it's shown on its first page") {
                    seeders.keys.forEach { listPath ->
                        withClue(listPath) {
                            val fragment = get("$listPath/fragments/list?page=3&name=nothing")

                            fragment.header(HtmxResponseHeaders.HX_REPLACE_URL) shouldBe "$listPath?name=nothing"
                            fragment.body() shouldContain """data-list-page="1""""
                        }
                    }
                }
            }
        }

        given("a page of the monitor lists, which is not loaded by htmx") {

            `when`("it's requested") {

                then("the URL is not replaced, since the page is loaded with it") {
                    seeders.keys.forEach { listPath ->
                        withClue(listPath) {
                            get("$listPath?page=2").header(HtmxResponseHeaders.HX_REPLACE_URL).shouldBeNull()
                        }
                    }
                }
            }
        }
    }
}
