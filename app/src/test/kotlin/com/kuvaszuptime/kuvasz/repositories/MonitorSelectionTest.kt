package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest(startApplication = false)
class MonitorSelectionTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        given("fetchAllWithDetails() with the two selectors") {

            `when`("both selectors are null") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")
                createHttpMonitor(httpMonitorRepository, monitorName = "uncategorized")

                then("every monitor is returned, which is what the default status page relies on") {
                    httpMonitorRepository.fetchAllWithDetails().map { it.name } shouldContainExactlyInAnyOrder
                        listOf("payments-http", "uncategorized")
                }
            }

            `when`("both selectors are empty") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")

                then("nothing is selected, the same way an empty custom page behaved before the categories") {
                    httpMonitorRepository.fetchAllWithDetails(
                        monitorNames = emptyList(),
                        categories = emptyList(),
                    ).shouldBeEmpty()
                }
            }

            `when`("only categories are given") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")
                createPushMonitor(pushMonitorRepository, monitorName = "payments-push", category = "Payments")
                createHttpMonitor(httpMonitorRepository, monitorName = "search-http", category = "Search")
                createHttpMonitor(httpMonitorRepository, monitorName = "uncategorized")

                then("every monitor of that category is selected, per type") {
                    httpMonitorRepository.fetchAllWithDetails(
                        monitorNames = emptyList(),
                        categories = listOf("Payments"),
                    ).map { it.name } shouldContainExactly listOf("payments-http")

                    pushMonitorRepository.fetchAllWithDetails(
                        monitorNames = emptyList(),
                        categories = listOf("Payments"),
                    ).map { it.name } shouldContainExactly listOf("payments-push")
                }
            }

            `when`("a monitor is selected by its name and by its category at the same time") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")
                createHttpMonitor(httpMonitorRepository, monitorName = "uncategorized")

                then("the two selectors are unioned and the monitor is returned only once") {
                    httpMonitorRepository.fetchAllWithDetails(
                        monitorNames = listOf("payments-http", "uncategorized"),
                        categories = listOf("Payments"),
                    ).map { it.name } shouldContainExactlyInAnyOrder listOf("payments-http", "uncategorized")
                }
            }

            `when`("a category is not in use by any monitor") {
                createHttpMonitor(httpMonitorRepository, monitorName = "named-one")

                then("it contributes nothing, while the monitor selector still applies") {
                    httpMonitorRepository.fetchAllWithDetails(
                        monitorNames = listOf("named-one"),
                        categories = listOf("Nobody uses me"),
                    ).map { it.name } shouldContainExactly listOf("named-one")

                    httpMonitorRepository.fetchAllWithDetails(
                        monitorNames = emptyList(),
                        categories = listOf("Nobody uses me"),
                    ).shouldBeEmpty()
                }
            }

            `when`("a disabled monitor belongs to a selected category") {
                createHttpMonitor(httpMonitorRepository, monitorName = "enabled-one", category = "Payments")
                createHttpMonitor(
                    httpMonitorRepository,
                    monitorName = "disabled-one",
                    category = "Payments",
                    enabled = false,
                )

                then("the enabled filter still wins over the category selector") {
                    httpMonitorRepository.fetchAllWithDetails(
                        enabled = true,
                        monitorNames = emptyList(),
                        categories = listOf("Payments"),
                    ).map { it.name } shouldContainExactly listOf("enabled-one")
                }
            }
        }

        given("the statusPages of a monitor") {

            `when`("a page references the monitor only by its category") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")
                createStatusPage(dslContext, slug = "by-category", categories = listOf("Payments"))

                then("the page is listed on the monitor") {
                    httpMonitorRepository.fetchAllWithDetails()
                        .single().statusPages shouldContainExactly setOf("by-category")
                }
            }

            `when`("a page references the monitor by its name and by its category as well") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")
                createStatusPage(
                    dslContext,
                    slug = "both-ways",
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, "payments-http")),
                    categories = listOf("Payments"),
                )

                then("the slug of the page is listed only once") {
                    httpMonitorRepository.fetchAllWithDetails()
                        .single().statusPages shouldContainExactly setOf("both-ways")
                }
            }

            `when`("the monitor is on one page by name and on another one by category") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")
                createStatusPage(
                    dslContext,
                    slug = "by-name",
                    monitors = listOf(MonitorID(MonitorType.HTTP_SSL, "payments-http")),
                )
                createStatusPage(dslContext, slug = "by-category", categories = listOf("Payments"))

                then("both slugs are listed") {
                    httpMonitorRepository.fetchAllWithDetails()
                        .single().statusPages shouldContainExactlyInAnyOrder setOf("by-name", "by-category")
                }
            }

            `when`("the monitor is not referenced by any page") {
                createHttpMonitor(httpMonitorRepository, monitorName = "payments-http", category = "Payments")
                createStatusPage(dslContext, slug = "another-category", categories = listOf("Search"))

                then("no slug is listed") {
                    httpMonitorRepository.fetchAllWithDetails().single().statusPages.shouldBeEmpty()
                }
            }
        }
    }
}
