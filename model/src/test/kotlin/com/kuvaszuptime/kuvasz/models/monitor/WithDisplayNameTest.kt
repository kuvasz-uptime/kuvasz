package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.IncidentType
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentDto
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.incident.effectiveMonitorName
import com.kuvaszuptime.kuvasz.models.dto.monitor.PushMonitorSummary
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class WithDisplayNameTest : BehaviorSpec({

    given("the effective name of a monitor") {

        `when`("the monitor has a display name") {
            then("the display name is shown") {
                PushMonitorRecord().setName("payments").setDisplayName("Payments API").effectiveName shouldBe
                    "Payments API"
                summary(displayName = "Payments API").effectiveName shouldBe "Payments API"
                incident(monitorDisplayName = "Payments API").effectiveMonitorName shouldBe "Payments API"
            }
        }

        `when`("the monitor has no display name") {
            then("its unique name is shown") {
                PushMonitorRecord().setName("payments").effectiveName shouldBe "payments"
                summary(displayName = null).effectiveName shouldBe "payments"
                incident(monitorDisplayName = null).effectiveMonitorName shouldBe "payments"
            }
        }
    }
})

private fun summary(displayName: String?) = PushMonitorSummary(
    id = 1,
    name = "payments",
    displayName = displayName,
    enabled = true,
    category = null,
    uptimeStatus = null,
)

private fun incident(monitorDisplayName: String?) = IncidentDto(
    monitorId = 1,
    monitorName = "payments",
    monitorDisplayName = monitorDisplayName,
    isMonitorEnabled = true,
    incidentType = IncidentType.PUSH,
    eventId = 1,
    status = IncidentStatus.ONGOING,
    details = null,
    startedAt = getCurrentTimestamp(),
    endedAt = null,
    updatedAt = getCurrentTimestamp(),
)
