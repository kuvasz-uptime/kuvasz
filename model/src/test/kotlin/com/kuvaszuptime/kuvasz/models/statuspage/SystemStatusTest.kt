package com.kuvaszuptime.kuvasz.models.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class SystemStatusTest : BehaviorSpec({

    fun monitor(
        uptimeStatus: UptimeStatus?,
        inMaintenance: Boolean = false,
    ) = StatusPageHttpMonitorDetailsDto(
        name = "monitor",
        lastCheck = null,
        uptimeRatio = null,
        uptimeStatus = uptimeStatus,
        uptimeStatusHistory = emptyList(),
        averageLatencyInMs = null,
        inMaintenance = inMaintenance,
    )

    given("SystemStatus.fromMonitors()") {

        `when`("there are no monitors") {
            then("it should be PENDING") {
                SystemStatus.fromMonitors(emptyList()) shouldBe SystemStatus.PENDING
            }
        }

        `when`("every monitor is UP") {
            val monitors = listOf(monitor(UptimeStatus.UP), monitor(UptimeStatus.UP))
            then("it should be OPERATIONAL") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.OPERATIONAL
            }
        }

        `when`("every monitor is DOWN") {
            val monitors = listOf(monitor(UptimeStatus.DOWN), monitor(UptimeStatus.DOWN))
            then("it should be MAJOR_OUTAGE") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.MAJOR_OUTAGE
            }
        }

        `when`("some monitors are UP and some are DOWN") {
            val monitors = listOf(monitor(UptimeStatus.UP), monitor(UptimeStatus.DOWN))
            then("it should be PARTIAL_OUTAGE") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.PARTIAL_OUTAGE
            }
        }

        `when`("a DOWN monitor is under maintenance too") {
            val monitors = listOf(monitor(UptimeStatus.UP), monitor(UptimeStatus.DOWN, inMaintenance = true))
            then("the outage should take precedence over the maintenance") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.PARTIAL_OUTAGE
            }
        }

        `when`("every monitor is under maintenance without an outage") {
            val monitors = listOf(
                monitor(UptimeStatus.UP, inMaintenance = true),
                monitor(null, inMaintenance = true),
            )
            then("it should be MAINTENANCE") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.MAINTENANCE
            }
        }

        `when`("some monitors are under maintenance without an outage") {
            val monitors = listOf(monitor(UptimeStatus.UP), monitor(UptimeStatus.UP, inMaintenance = true))
            then("it should be PARTIAL_MAINTENANCE") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.PARTIAL_MAINTENANCE
            }
        }

        `when`("some monitors don't have a status yet") {
            val monitors = listOf(monitor(UptimeStatus.UP), monitor(null))
            then("it should be PENDING") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.PENDING
            }
        }

        `when`("a DOWN monitor is accompanied only by monitors without a status") {
            val monitors = listOf(monitor(UptimeStatus.DOWN), monitor(null))
            then("it should be PENDING") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.PENDING
            }
        }

        `when`("every monitor is DOWN while all of them are under maintenance") {
            val monitors = listOf(
                monitor(UptimeStatus.DOWN, inMaintenance = true),
                monitor(UptimeStatus.DOWN, inMaintenance = true),
            )
            then("the outage should take precedence over the maintenance") {
                SystemStatus.fromMonitors(monitors) shouldBe SystemStatus.MAJOR_OUTAGE
            }
        }
    }
})
