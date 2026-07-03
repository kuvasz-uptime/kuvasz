package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowCreateDto
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEndEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowEvent
import com.kuvaszuptime.kuvasz.models.events.MaintenanceWindowStartEvent
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import org.jooq.DSLContext
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.util.concurrent.CopyOnWriteArrayList

@MicronautTest(environments = ["full-integrations-setup"])
class MaintenanceWindowActionsTest(
    private val actions: MaintenanceWindowActions,
    private val scheduler: MaintenanceWindowScheduler,
    private val dsl: DSLContext,
    eventDispatcher: EventDispatcher,
) : DatabaseBehaviorSpec({

    val mapper = jacksonObjectMapper()

    val received = CopyOnWriteArrayList<MaintenanceWindowEvent>()
    eventDispatcher.subscribeToMaintenanceStartEvents { received.add(it) }
    eventDispatcher.subscribeToMaintenanceEndEvents { received.add(it) }
    beforeContainer { received.clear() }

    given("the MaintenanceWindowActions - manual toggle") {

        `when`("a disabled manual window is enabled") {
            val window = createMaintenanceWindow(dsl, enabled = false)

            actions.updateMaintenanceWindow(window.id, mapper.createObjectNode().put("enabled", true))

            then("a start event is emitted for the window") {
                received.single().let { event ->
                    event.shouldBeInstanceOf<MaintenanceWindowStartEvent>()
                    event.window.id shouldBe window.id
                }
            }
        }

        `when`("an enabled manual window is disabled") {
            val window = createMaintenanceWindow(dsl, enabled = true)

            actions.updateMaintenanceWindow(window.id, mapper.createObjectNode().put("enabled", false))

            then("an end event is emitted for the window") {
                received.single().let { event ->
                    event.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
                    event.window.id shouldBe window.id
                }
            }
        }

        `when`("a time-based (single) window is toggled") {
            val window = createMaintenanceWindow(
                dsl,
                enabled = false,
                start = getCurrentTimestamp().plusHours(1),
                duration = "PT1H",
            )

            actions.updateMaintenanceWindow(window.id, mapper.createObjectNode().put("enabled", true))

            then("no manual event is emitted, but the window gets (re)scheduled") {
                received.shouldBeEmpty()
                scheduler.getScheduledWindows() shouldContainKey window.id
            }
        }

        `when`("an active time-based window is disabled") {
            val window = createMaintenanceWindow(
                dsl,
                enabled = true,
                start = getCurrentTimestamp().minusMinutes(10),
                duration = "PT1H",
            )

            actions.updateMaintenanceWindow(window.id, mapper.createObjectNode().put("enabled", false))

            then("an end event is emitted so the maintenance state is closed") {
                received.single().let { event ->
                    event.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
                    event.window.id shouldBe window.id
                }
                scheduler.getScheduledWindows() shouldNotContainKey window.id
            }
        }
    }

    given("the MaintenanceWindowActions - create & delete") {

        `when`("a future single window is created") {
            val created = actions.createMaintenanceWindow(
                MaintenanceWindowCreateDto(
                    name = "Created window",
                    start = getCurrentTimestamp().plusHours(1),
                    duration = "PT1H",
                )
            )

            then("it is scheduled and no start event is emitted yet") {
                scheduler.getScheduledWindows() shouldContainKey created.id
                received.shouldBeEmpty()
            }
        }

        `when`("an enabled manual window is created") {
            val created = actions.createMaintenanceWindow(
                MaintenanceWindowCreateDto(
                    name = "Active on creation",
                    enabled = true,
                    global = true,
                )
            )

            then("a start event is emitted so consumers learn maintenance has begun") {
                received.single().let { event ->
                    event.shouldBeInstanceOf<MaintenanceWindowStartEvent>()
                    event.window.id shouldBe created.id
                }
            }
        }

        `when`("a scheduled window is deleted") {
            val created = actions.createMaintenanceWindow(
                MaintenanceWindowCreateDto(
                    name = "To be deleted",
                    start = getCurrentTimestamp().plusHours(1),
                    duration = "PT1H",
                )
            )
            scheduler.getScheduledWindows() shouldContainKey created.id

            actions.deleteMaintenanceWindowById(created.id)

            then("its scheduled tasks are cancelled") {
                scheduler.getScheduledWindows() shouldNotContainKey created.id
            }
        }

        `when`("an active window is deleted") {
            val window = createMaintenanceWindow(
                dsl,
                enabled = true,
                start = getCurrentTimestamp().minusMinutes(10),
                duration = "PT1H",
            )

            actions.deleteMaintenanceWindowById(window.id)

            then("an end event is emitted so the maintenance state is closed") {
                received.single().let { event ->
                    event.shouldBeInstanceOf<MaintenanceWindowEndEvent>()
                    event.window.id shouldBe window.id
                }
                scheduler.getScheduledWindows() shouldNotContainKey window.id
            }
        }
    }
})
