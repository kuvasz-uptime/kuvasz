package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MaintenanceWindowDuplicatedException
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.jooq.DSLContext

@MicronautTest
class MaintenanceWindowRepositoryTest(
    private val repository: MaintenanceWindowRepository,
    private val dsl: DSLContext,
) : DatabaseBehaviorSpec({

    val coveredMonitor = MonitorID(MonitorType.HTTP_SSL, "covered")
    val otherMonitor = MonitorID(MonitorType.HTTP_SSL, "other")

    given("fetchByEnabled") {
        `when`("there are enabled and disabled windows") {
            createMaintenanceWindow(dsl, name = "enabled-1", enabled = true)
            createMaintenanceWindow(dsl, name = "enabled-2", enabled = true)
            createMaintenanceWindow(dsl, name = "disabled", enabled = false)

            then("it returns only the windows with the requested enabled flag") {
                repository.fetchByEnabled(true).map { it.name } shouldContainExactlyInAnyOrder
                    listOf("enabled-1", "enabled-2")
                repository.fetchByEnabled(false).map { it.name } shouldContainExactly listOf("disabled")
            }
        }
    }

    given("findActiveCandidatesForMonitor") {
        `when`("there are global, explicitly-assigned, unrelated and disabled windows") {
            createMaintenanceWindow(dsl, name = "global", enabled = true, global = true)
            createMaintenanceWindow(dsl, name = "assigned", enabled = true, monitors = listOf(coveredMonitor))
            createMaintenanceWindow(dsl, name = "unrelated", enabled = true, monitors = listOf(otherMonitor))
            createMaintenanceWindow(
                dsl,
                name = "disabled-global",
                enabled = false,
                global = true,
            )

            then("it returns the enabled global and explicitly-assigned windows only") {
                repository.findActiveCandidatesForMonitor(coveredMonitor).map { it.name } shouldContainExactlyInAnyOrder
                    listOf("global", "assigned")
            }
        }
    }

    given("upsert") {
        `when`("a window with the same name already exists") {
            createMaintenanceWindow(dsl, name = "to-upsert", description = "original", global = false)

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
            createMaintenanceWindow(dsl, name = "duplicate")

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
            val keep = createMaintenanceWindow(dsl, name = "keep")
            createMaintenanceWindow(dsl, name = "remove-1")
            createMaintenanceWindow(dsl, name = "remove-2")

            then("it deletes every window except the given IDs") {
                val deleted = repository.deleteAllExcept(listOf(keep.id))

                deleted shouldBe 2
                repository.fetchAll().map { it.name } shouldContainExactly listOf("keep")
            }
        }
    }
})
