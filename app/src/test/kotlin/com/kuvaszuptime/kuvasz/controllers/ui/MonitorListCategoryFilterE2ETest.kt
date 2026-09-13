package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * The category filter of the monitor lists distinguishes three states, and two of them differ only in whether the
 * `category` query parameter is **present but empty** or **absent altogether**. That difference is resolved by
 * Micronaut's parameter binding, so it cannot be proven by calling the controller directly with a `String?` - these
 * cases go over the wire with a real client, exactly as a browser would send them.
 */
@MicronautTest
@Property(name = "micronaut.security.enabled", value = "false")
class MonitorListCategoryFilterE2ETest(
    @Client("/") private val client: HttpClient,
    private val httpMonitorRepository: HttpMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        fun get(uri: String): String = client.toBlocking().retrieve(HttpRequest.GET<Any>(uri))

        given("the monitor list fragment over HTTP") {

            `when`("the category parameter is absent from the URL") {
                createHttpMonitor(httpMonitorRepository, monitorName = "categorized", category = "Payments")
                createHttpMonitor(httpMonitorRepository, monitorName = "uncategorized", category = null)

                val html = get("/http-monitors/fragments/list")

                then("nothing is filtered out") {
                    html shouldContain "categorized"
                    html shouldContain "uncategorized"
                }
            }

            `when`("the category parameter is present but empty") {
                createHttpMonitor(httpMonitorRepository, monitorName = "categorized", category = "Payments")
                createHttpMonitor(httpMonitorRepository, monitorName = "uncategorized", category = null)

                val html = get("/http-monitors/fragments/list?category=")

                then("only the monitors without a category are listed") {
                    html shouldContain "uncategorized"
                    // The categorized one must be gone, which is what tells an empty parameter from an absent one
                    html shouldNotContain ">categorized<"
                }
            }

            `when`("the category parameter carries a category") {
                createHttpMonitor(httpMonitorRepository, monitorName = "categorized", category = "Payments")
                createHttpMonitor(httpMonitorRepository, monitorName = "uncategorized", category = null)

                val html = get("/http-monitors/fragments/list?category=Payments")

                then("only the monitors of that category are listed") {
                    html shouldContain "categorized"
                    html shouldNotContain ">uncategorized<"
                }
            }

            `when`("the category needs escaping in the URL") {
                createHttpMonitor(httpMonitorRepository, monitorName = "spaced", category = "Payments & Billing")
                createHttpMonitor(httpMonitorRepository, monitorName = "other", category = "Search")

                val html = get("/http-monitors/fragments/list?category=Payments%20%26%20Billing")

                then("it still resolves to the right category") {
                    html shouldContain "spaced"
                    html shouldNotContain ">other<"
                }
            }
        }

        given("the monitor list page over HTTP") {

            `when`("the category parameter is absent") {
                createHttpMonitor(httpMonitorRepository, monitorName = "categorized", category = "Payments")

                val html = get("/http-monitors")

                then("the htmx endpoint of the list is left unfiltered too") {
                    html shouldContain """hx-get="/http-monitors/fragments/list""""
                    html shouldNotContain """hx-get="/http-monitors/fragments/list?category"""
                }
            }

            `when`("the category parameter is present but empty") {
                createHttpMonitor(httpMonitorRepository, monitorName = "categorized", category = "Payments")

                val html = get("/http-monitors?category=")

                then("the filter is baked into the htmx endpoint, so the periodic refresh keeps it") {
                    html shouldContain """hx-get="/http-monitors/fragments/list?category=""""
                }
            }

            `when`("the category parameter carries a category that needs escaping") {
                createHttpMonitor(httpMonitorRepository, monitorName = "spaced", category = "Payments & Billing")

                val html = get("/http-monitors?category=Payments%20%26%20Billing")

                then("it is escaped again into the htmx endpoint") {
                    html shouldContain "hx-get=\"/http-monitors/fragments/list?category=Payments+%26+Billing\""
                }
            }

            `when`("the type has no categorized monitor at all") {
                createHttpMonitor(httpMonitorRepository, monitorName = "uncategorized", category = null)

                val html = get("/http-monitors")

                then("the filter is still rendered, with the two options that describe that state") {
                    html shouldContain "category-filter"
                    html shouldContain Messages.allCategories()
                    html shouldContain Messages.uncategorizedMonitors()
                }
            }
        }
    }
}
