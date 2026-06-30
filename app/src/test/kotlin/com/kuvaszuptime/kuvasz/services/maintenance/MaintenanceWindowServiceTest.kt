package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.mocks.createMaintenanceWindow
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest

@MicronautTest
class MaintenanceWindowServiceTest(
    private val service: MaintenanceWindowService,
) : DatabaseBehaviorSpec() {

    init {
        val monitor = MonitorID(MonitorType.HTTP_SSL, "covered")
        val otherMonitor = MonitorID(MonitorType.HTTP_SSL, "other")
        val now = getCurrentTimestamp()

        given("isUnderMaintenance") {
            `when`("an enabled manual global window exists") {
                createMaintenanceWindow(dslContext, name = "manual-global", enabled = true, global = true)

                then("the monitor is under maintenance") {
                    service.isUnderMaintenance(monitor) shouldBe true
                }
            }

            `when`("only a disabled global window exists") {
                createMaintenanceWindow(dslContext, name = "disabled", enabled = false, global = true)

                then("the monitor is not under maintenance") {
                    service.isUnderMaintenance(monitor) shouldBe false
                }
            }

            `when`("a window is assigned to a different monitor only") {
                createMaintenanceWindow(
                    dslContext,
                    name = "other-assigned",
                    enabled = true,
                    monitors = listOf(otherMonitor)
                )

                then("the monitor is not under maintenance") {
                    service.isUnderMaintenance(monitor) shouldBe false
                }
            }

            `when`("an active single window is assigned to the monitor") {
                createMaintenanceWindow(
                    dslContext,
                    name = "single-active",
                    enabled = true,
                    monitors = listOf(monitor),
                    start = now.minusMinutes(30),
                    duration = "PT1H",
                )

                then("the monitor is under maintenance") {
                    service.isUnderMaintenance(monitor) shouldBe true
                }
            }

            `when`("a single window assigned to the monitor is already over") {
                createMaintenanceWindow(
                    dslContext,
                    name = "single-past",
                    enabled = true,
                    monitors = listOf(monitor),
                    start = now.minusHours(3),
                    duration = "PT1H",
                )

                then("the monitor is not under maintenance") {
                    service.isUnderMaintenance(monitor) shouldBe false
                }
            }

            `when`("an active cron window is assigned to the monitor") {
                createMaintenanceWindow(
                    dslContext,
                    name = "cron-active",
                    enabled = true,
                    monitors = listOf(monitor),
                    cron = "* * * * *",
                    duration = "PT5M",
                )

                then("the monitor is under maintenance") {
                    service.isUnderMaintenance(monitor) shouldBe true
                }
            }
        }

        given("getWindowsForMonitor") {
            `when`("the monitor is affected by active and inactive windows, plus an unrelated one") {
                createMaintenanceWindow(dslContext, name = "active-manual", enabled = true, global = true)
                createMaintenanceWindow(
                    dslContext,
                    name = "inactive-single",
                    enabled = true,
                    monitors = listOf(monitor),
                    start = now.minusHours(3),
                    duration = "PT1H",
                )
                createMaintenanceWindow(dslContext, name = "unrelated", enabled = true, monitors = listOf(otherMonitor))

                then("it returns only the affecting windows with their current activity status") {
                    val windows = service.getWindowsForMonitor(monitor)

                    windows.map { it.name } shouldContainExactlyInAnyOrder listOf("active-manual", "inactive-single")
                    windows.single { it.name == "active-manual" }.active shouldBe true
                    windows.single { it.name == "inactive-single" }.active shouldBe false
                }
            }
        }
    }
}
