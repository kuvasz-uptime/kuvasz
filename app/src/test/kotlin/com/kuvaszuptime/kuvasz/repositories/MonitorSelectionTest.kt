package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createDnsMonitor
import com.kuvaszuptime.kuvasz.mocks.createHttpMonitor
import com.kuvaszuptime.kuvasz.mocks.createIcmpMonitor
import com.kuvaszuptime.kuvasz.mocks.createPushMonitor
import com.kuvaszuptime.kuvasz.mocks.createStatusPage
import com.kuvaszuptime.kuvasz.mocks.createTcpMonitor
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

/**
 * The selection a status page expresses is resolved in SQL, and every monitor repository wires the very same
 * [MonitorRepository.selectionCondition] and category join into its own query. Both are easy to get subtly wrong
 * per type, so every case below runs against all five repositories.
 *
 * The two selectors are additive, and both of them being null means "no restriction at all", which is how the
 * default status page collects every enabled monitor.
 */
@MicronautTest(startApplication = false)
class MonitorSelectionTest(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : DatabaseBehaviorSpec() {

    init {
        /**
         * One monitor type to run the whole selection suite against: its repository and a way to seed a monitor of
         * that type with the only three properties the selection cares about.
         */
        data class TypeUnderTest(
            val monitorType: MonitorType,
            val repository: MonitorRepository<*, *>,
            val seed: (name: String, category: String?, enabled: Boolean) -> Unit,
        )

        val typesUnderTest = listOf(
            TypeUnderTest(MonitorType.HTTP_SSL, httpMonitorRepository) { name, category, enabled ->
                createHttpMonitor(httpMonitorRepository, monitorName = name, category = category, enabled = enabled)
            },
            TypeUnderTest(MonitorType.PUSH, pushMonitorRepository) { name, category, enabled ->
                createPushMonitor(pushMonitorRepository, monitorName = name, category = category, enabled = enabled)
            },
            TypeUnderTest(MonitorType.ICMP, icmpMonitorRepository) { name, category, enabled ->
                createIcmpMonitor(icmpMonitorRepository, monitorName = name, category = category, enabled = enabled)
            },
            TypeUnderTest(MonitorType.TCP, tcpMonitorRepository) { name, category, enabled ->
                createTcpMonitor(tcpMonitorRepository, monitorName = name, category = category, enabled = enabled)
            },
            TypeUnderTest(MonitorType.DNS, dnsMonitorRepository) { name, category, enabled ->
                createDnsMonitor(dnsMonitorRepository, monitorName = name, category = category, enabled = enabled)
            },
        )

        typesUnderTest.forEach { type ->
            val identifier = type.monitorType.identifier

            given("the selectors of fetchAllWithDetails() on the $identifier repository") {

                `when`("both selectors are null") {
                    type.seed("categorized", "Payments", true)
                    type.seed("uncategorized", null, true)

                    then("every monitor is returned, which is what the default status page relies on") {
                        type.repository.fetchAllWithDetails().map { it.name } shouldContainExactlyInAnyOrder
                            listOf("categorized", "uncategorized")
                    }
                }

                `when`("both selectors are empty") {
                    type.seed("categorized", "Payments", true)

                    then("nothing is selected, the same way an empty custom page behaved before the categories") {
                        type.repository.fetchAllWithDetails(
                            monitorNames = emptyList(),
                            categories = emptyList(),
                        ).shouldBeEmpty()
                    }
                }

                `when`("only the categories are given") {
                    type.seed("payments", "Payments", true)
                    type.seed("search", "Search", true)
                    type.seed("uncategorized", null, true)

                    then("only the monitors of that category are selected") {
                        type.repository.fetchAllWithDetails(
                            monitorNames = emptyList(),
                            categories = listOf("Payments"),
                        ).map { it.name } shouldContainExactly listOf("payments")
                    }
                }

                `when`("only the monitor names are given") {
                    type.seed("payments", "Payments", true)
                    type.seed("search", "Search", true)

                    then("the category of the monitors is irrelevant") {
                        type.repository.fetchAllWithDetails(
                            monitorNames = listOf("search"),
                            categories = emptyList(),
                        ).map { it.name } shouldContainExactly listOf("search")
                    }
                }

                `when`("a monitor is selected by its name and by its category at the same time") {
                    type.seed("payments", "Payments", true)
                    type.seed("uncategorized", null, true)

                    then("the two selectors are unioned and the monitor is returned only once") {
                        type.repository.fetchAllWithDetails(
                            monitorNames = listOf("payments", "uncategorized"),
                            categories = listOf("Payments"),
                        ).map { it.name } shouldContainExactlyInAnyOrder listOf("payments", "uncategorized")
                    }
                }

                `when`("a category is not in use by any monitor") {
                    type.seed("named-one", null, true)

                    then("it contributes nothing, while the monitor selector still applies") {
                        type.repository.fetchAllWithDetails(
                            monitorNames = listOf("named-one"),
                            categories = listOf("Nobody uses me"),
                        ).map { it.name } shouldContainExactly listOf("named-one")

                        type.repository.fetchAllWithDetails(
                            monitorNames = emptyList(),
                            categories = listOf("Nobody uses me"),
                        ).shouldBeEmpty()
                    }
                }

                `when`("a category reference only differs in its casing") {
                    type.seed("payments", "Payments", true)

                    then("it does not match, mirroring the case-sensitive category of the monitors") {
                        type.repository.fetchAllWithDetails(
                            monitorNames = emptyList(),
                            categories = listOf("payments"),
                        ).shouldBeEmpty()
                    }
                }

                `when`("a disabled monitor belongs to a selected category") {
                    type.seed("enabled-one", "Payments", true)
                    type.seed("disabled-one", "Payments", false)

                    then("the enabled filter still wins over the category selector") {
                        type.repository.fetchAllWithDetails(
                            enabled = true,
                            monitorNames = emptyList(),
                            categories = listOf("Payments"),
                        ).map { it.name } shouldContainExactly listOf("enabled-one")
                    }
                }
            }

            given("the statusPages of a $identifier monitor") {

                `when`("a page references the monitor only by its category") {
                    type.seed("payments", "Payments", true)
                    createStatusPage(dslContext, slug = "by-category", categories = listOf("Payments"))

                    then("the page is listed on the monitor") {
                        type.repository.fetchAllWithDetails()
                            .single().statusPages shouldContainExactly setOf("by-category")
                    }
                }

                `when`("a page references the monitor by its name and by its category as well") {
                    type.seed("payments", "Payments", true)
                    createStatusPage(
                        dslContext,
                        slug = "both-ways",
                        monitors = listOf(MonitorID(type.monitorType, "payments")),
                        categories = listOf("Payments"),
                    )

                    then("the slug of the page is listed only once") {
                        type.repository.fetchAllWithDetails()
                            .single().statusPages shouldContainExactly setOf("both-ways")
                    }
                }

                `when`("the monitor is on one page by name and on another one by category") {
                    type.seed("payments", "Payments", true)
                    createStatusPage(
                        dslContext,
                        slug = "by-name",
                        monitors = listOf(MonitorID(type.monitorType, "payments")),
                    )
                    createStatusPage(dslContext, slug = "by-category", categories = listOf("Payments"))

                    then("both slugs are listed") {
                        type.repository.fetchAllWithDetails()
                            .single().statusPages shouldContainExactlyInAnyOrder setOf("by-name", "by-category")
                    }
                }

                `when`("only the category of another monitor is referenced by a page") {
                    type.seed("payments", "Payments", true)
                    createStatusPage(dslContext, slug = "another-category", categories = listOf("Search"))

                    then("no slug is listed") {
                        type.repository.fetchAllWithDetails().single().statusPages.shouldBeEmpty()
                    }
                }
            }
        }
    }
}
