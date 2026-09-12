package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MaintenanceWindowDuplicatedException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.kotest.matchers.maps.shouldBeEmpty as shouldBeEmptyMap

@MicronautTest
class MaintenanceWindowRepositoryTest(
    private val repository: MaintenanceWindowRepository,
) : DatabaseBehaviorSpec() {
    init {

        val coveredMonitor = MonitorID(MonitorType.HTTP_SSL, "covered")
        val otherMonitor = MonitorID(MonitorType.HTTP_SSL, "other")

        given("fetchByEnabled") {
            `when`("there are enabled and disabled windows") {
                createMaintenanceWindow(dslContext, name = "enabled-1", enabled = true)
                createMaintenanceWindow(dslContext, name = "enabled-2", enabled = true)
                createMaintenanceWindow(dslContext, name = "disabled", enabled = false)

                then("it returns only the windows with the requested enabled flag") {
                    repository.fetchByEnabled(true).map { it.name } shouldContainExactlyInAnyOrder
                        listOf("enabled-1", "enabled-2")
                    repository.fetchByEnabled(false).map { it.name } shouldContainExactly listOf("disabled")
                }
            }
        }

        given("findActiveCandidatesForMonitor") {
            `when`("there are global, explicitly-assigned, unrelated and disabled windows") {
                createMaintenanceWindow(dslContext, name = "global", enabled = true, global = true)
                createMaintenanceWindow(
                    dslContext,
                    name = "assigned",
                    enabled = true,
                    monitors = listOf(coveredMonitor)
                )
                createMaintenanceWindow(dslContext, name = "unrelated", enabled = true, monitors = listOf(otherMonitor))
                createMaintenanceWindow(
                    dslContext,
                    name = "disabled-global",
                    enabled = false,
                    global = true,
                )

                then("it returns the enabled global and explicitly-assigned windows only") {
                    repository.findActiveCandidatesForMonitor(coveredMonitor, null)
                        .map { it.name } shouldContainExactlyInAnyOrder
                        listOf("global", "assigned")
                }
            }

            `when`("a window covers the category the monitor currently belongs to") {
                createMaintenanceWindow(
                    dslContext,
                    name = "by-category",
                    enabled = true,
                    categories = listOf("Payments"),
                )
                createMaintenanceWindow(
                    dslContext,
                    name = "other-category",
                    enabled = true,
                    categories = listOf("Search"),
                )
                createMaintenanceWindow(
                    dslContext,
                    name = "disabled-category",
                    enabled = false,
                    categories = listOf("Payments"),
                )

                then("only the enabled window of that category is returned") {
                    repository.findActiveCandidatesForMonitor(coveredMonitor, "Payments")
                        .map { it.name } shouldContainExactly listOf("by-category")
                }

                then("an uncategorized monitor is not reached by any of them") {
                    repository.findActiveCandidatesForMonitor(coveredMonitor, null).shouldBeEmpty()
                }

                then("a category reference only differing in its casing does not match") {
                    repository.findActiveCandidatesForMonitor(coveredMonitor, "payments").shouldBeEmpty()
                }
            }

            `when`("a window lists the monitor explicitly and another one covers its category") {
                createMaintenanceWindow(
                    dslContext,
                    name = "assigned",
                    enabled = true,
                    monitors = listOf(coveredMonitor),
                )
                createMaintenanceWindow(
                    dslContext,
                    name = "by-category",
                    enabled = true,
                    categories = listOf("Payments"),
                )

                then("both are returned, the two selectors being additive") {
                    repository.findActiveCandidatesForMonitor(coveredMonitor, "Payments")
                        .map { it.name } shouldContainExactlyInAnyOrder listOf("assigned", "by-category")
                }
            }

            `when`("a single window lists the monitor and covers its category as well") {
                createMaintenanceWindow(
                    dslContext,
                    name = "both-ways",
                    enabled = true,
                    monitors = listOf(coveredMonitor),
                    categories = listOf("Payments"),
                )

                then("it is returned only once") {
                    repository.findActiveCandidatesForMonitor(coveredMonitor, "Payments")
                        .map { it.name } shouldContainExactly listOf("both-ways")
                }
            }
        }

        given("findActiveCandidatesForMonitors") {
            `when`("resolving several monitors against global, assigned, unrelated and disabled windows") {
                createMaintenanceWindow(dslContext, name = "global", enabled = true, global = true)
                createMaintenanceWindow(
                    dslContext,
                    name = "assigned",
                    enabled = true,
                    monitors = listOf(coveredMonitor)
                )
                createMaintenanceWindow(dslContext, name = "unrelated", enabled = true, monitors = listOf(otherMonitor))
                createMaintenanceWindow(dslContext, name = "disabled-global", enabled = false, global = true)

                then("each monitor is keyed to only the enabled windows affecting it, in a single query") {
                    val result = repository.findActiveCandidatesForMonitors(
                        mapOf(coveredMonitor to null, otherMonitor to null)
                    )

                    result.getValue(coveredMonitor).map { it.name } shouldContainExactlyInAnyOrder
                        listOf("global", "assigned")
                    result.getValue(otherMonitor).map { it.name } shouldContainExactlyInAnyOrder
                        listOf("global", "unrelated")
                }
            }

            `when`("a requested monitor is affected by no window") {
                createMaintenanceWindow(
                    dslContext,
                    name = "assigned",
                    enabled = true,
                    monitors = listOf(coveredMonitor)
                )

                then("it still gets an (empty) entry") {
                    val result = repository.findActiveCandidatesForMonitors(
                        mapOf(coveredMonitor to null, otherMonitor to null)
                    )

                    result.getValue(coveredMonitor).map { it.name } shouldContainExactly listOf("assigned")
                    result.getValue(otherMonitor).shouldBeEmpty()
                }
            }

            `when`("the requested monitors belong to different categories") {
                createMaintenanceWindow(
                    dslContext,
                    name = "payments",
                    enabled = true,
                    categories = listOf("Payments"),
                )
                createMaintenanceWindow(dslContext, name = "search", enabled = true, categories = listOf("Search"))
                createMaintenanceWindow(dslContext, name = "global", enabled = true, global = true)

                then("every monitor is matched against its own category within the single query") {
                    val result = repository.findActiveCandidatesForMonitors(
                        mapOf(coveredMonitor to "Payments", otherMonitor to "Search")
                    )

                    result.getValue(coveredMonitor).map { it.name } shouldContainExactlyInAnyOrder
                        listOf("global", "payments")
                    result.getValue(otherMonitor).map { it.name } shouldContainExactlyInAnyOrder
                        listOf("global", "search")
                }
            }

            `when`("one of the requested monitors is uncategorized") {
                createMaintenanceWindow(
                    dslContext,
                    name = "payments",
                    enabled = true,
                    categories = listOf("Payments"),
                )

                then("the null category matches nothing instead of erroring out") {
                    val result = repository.findActiveCandidatesForMonitors(
                        mapOf(coveredMonitor to "Payments", otherMonitor to null)
                    )

                    result.getValue(coveredMonitor).map { it.name } shouldContainExactly listOf("payments")
                    result.getValue(otherMonitor).shouldBeEmpty()
                }
            }

            `when`("no monitors are requested") {
                createMaintenanceWindow(dslContext, name = "global", enabled = true, global = true)

                then("it returns an empty map without querying") {
                    repository.findActiveCandidatesForMonitors(emptyMap()).shouldBeEmptyMap()
                }
            }
        }

        given("upsert") {
            `when`("a window with the same name already exists") {
                createMaintenanceWindow(dslContext, name = "to-upsert", description = "original", global = false)

                then("it updates the existing row instead of inserting a new one") {
                    repository.upsert(
                        MaintenanceWindowRecord()
                            .setName("to-upsert")
                            .setDescription("updated")
                            .setEnabled(true)
                            .setGlobal(true)
                            .setShowOnStatusPages(false)
                            .setMonitors(emptyArray())
                            .setIntegrations(emptyArray())
                    )

                    val all = repository.fetchAll() shouldHaveSize 1
                    all.single().description shouldBe "updated"
                    all.single().global shouldBe true
                }
            }
        }

        given("returningInsert") {
            `when`("a window with a duplicated name is inserted") {
                createMaintenanceWindow(dslContext, name = "duplicate")

                then("it throws a MaintenanceWindowDuplicatedException") {
                    shouldThrow<MaintenanceWindowDuplicatedException> {
                        repository.returningInsert(
                            MaintenanceWindowRecord()
                                .setName("duplicate")
                                .setEnabled(true)
                                .setGlobal(false)
                                .setShowOnStatusPages(false)
                                .setMonitors(emptyArray())
                                .setIntegrations(emptyArray())
                        )
                    }
                }
            }
        }

        given("deleteAllExcept") {
            `when`("there are several windows") {
                val keep = createMaintenanceWindow(dslContext, name = "keep")
                createMaintenanceWindow(dslContext, name = "remove-1")
                createMaintenanceWindow(dslContext, name = "remove-2")

                then("it deletes every window except the given IDs and returns their names") {
                    val deleted = repository.deleteAllExcept(listOf(keep.id))

                    deleted shouldContainExactlyInAnyOrder listOf("remove-1", "remove-2")
                    repository.fetchAll().map { it.name } shouldContainExactly listOf("keep")
                }
            }
        }
    }
}
