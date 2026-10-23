package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import io.kotest.assertions.withClue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
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
 * The HTTP contract of the status page and the maintenance window lists of the web UI: the binding of their query
 * parameters, and the URL their fragments give htmx to replace the one of the page with. What the lists render is
 * covered by the UI tests.
 */
@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class WebUIStatusPageAndMaintenanceListControllerTest(
    @Client("/") private val client: HttpClient,
) : DatabaseBehaviorSpec() {

    init {
        suspend fun get(uri: String): HttpResponse<String> =
            client.exchange(HttpRequest.GET<Any>(uri), String::class.java).awaitFirst()

        suspend fun replacedUrlOf(uri: String): String? = get(uri).header(HtmxResponseHeaders.HX_REPLACE_URL)

        // The path of each list, with its own filters and a way to seed one of its items
        val lists = listOf(
            ListUnderTest(
                path = "/status-pages",
                filterQuery = "name=+acme+&public=true",
                canonicalFilterQuery = "name=acme&public=true",
                sortedBy = "SLUG",
            ) { index -> createStatusPage(dslContext, title = "Page $index") },
            ListUnderTest(
                path = "/maintenance-windows",
                filterQuery = "name=+acme+&enabled=false",
                canonicalFilterQuery = "name=acme&enabled=false",
                sortedBy = "START",
            ) { index -> createMaintenanceWindow(dslContext, name = "Window $index") },
        )

        given("the status page and the maintenance window lists") {

            `when`("the query parameters are valid") {

                then("the fragment replaces the URL of the page with the one leaving out the defaults") {
                    lists.forEach { list ->
                        withClue(list.path) {
                            val query = "${list.filterQuery}&sortedBy=${list.sortedBy}&sortOrder=DESC&page=1&size=25"
                            get("${list.path}?$query").status shouldBe HttpStatus.OK

                            replacedUrlOf("${list.path}/fragments/list?$query") shouldBe
                                "${list.path}?${list.canonicalFilterQuery}&sortedBy=${list.sortedBy}&sortOrder=DESC"
                        }
                    }
                }
            }

            `when`("the query parameters are unknown or out of range, e.g. in a URL edited by hand") {

                then("they are ignored instead of failing the request") {
                    lists.forEach { list ->
                        withClue(list.path) {
                            val query = "page=0&size=7&sortedBy=FOO&public=maybe&enabled=maybe"
                            get("${list.path}?$query").status shouldBe HttpStatus.OK
                            replacedUrlOf("${list.path}/fragments/list?$query") shouldBe list.path
                        }
                    }
                }
            }

            `when`("the requested page is past the end of a list") {

                then("its last page is shown, and put into the URL") {
                    lists.forEach { list ->
                        withClue(list.path) {
                            (1..ITEM_COUNT).forEach { list.seed(it) }

                            replacedUrlOf("${list.path}/fragments/list?size=10&page=5") shouldBe
                                "${list.path}?page=2&size=10"
                        }
                    }
                }
            }

            `when`("their pages are requested") {

                then("the URL is not replaced, since the page is loaded with it") {
                    lists.forEach { list ->
                        withClue(list.path) {
                            get("${list.path}?page=2").header(HtmxResponseHeaders.HX_REPLACE_URL).shouldBeNull()
                        }
                    }
                }
            }
        }
    }

    private class ListUnderTest(
        val path: String,
        val filterQuery: String,
        val canonicalFilterQuery: String,
        val sortedBy: String,
        val seed: (Int) -> Unit,
    )

    private companion object {
        const val ITEM_COUNT = 11
    }
}
