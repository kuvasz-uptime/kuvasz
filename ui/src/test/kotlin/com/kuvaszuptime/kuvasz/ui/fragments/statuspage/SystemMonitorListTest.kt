package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentDto
import com.kuvaszuptime.kuvasz.ui.utils.*
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.LocalDate
import java.time.OffsetDateTime

private fun incident(startedAt: String, endedAt: String?) = incident(
    OffsetDateTime.parse(startedAt),
    endedAt?.let { OffsetDateTime.parse(it) },
)

private fun incident(startedAt: OffsetDateTime, endedAt: OffsetDateTime?) = StatusPageIncidentDto(
    id = 1,
    startedAt = startedAt,
    endedAt = endedAt,
    status = if (endedAt == null) IncidentStatus.ONGOING else IncidentStatus.RESOLVED,
    notes = emptyList(),
)

// The tests run in Europe/Vienna, which is UTC+2 in the summer and UTC+1 in the winter
class SystemMonitorListTest : BehaviorSpec({

    given("the time span of an incident on a day of the history") {
        val day = LocalDate.parse("2026-07-10")

        `when`("the incident started and ended on the day") {
            val incident = incident("2026-07-10T08:00:00Z", "2026-07-10T08:30:00Z")

            then("it's shown as it is, in the system's time zone") {
                incident.spanOnDay(day) shouldBe "2026/07/10 10:00:00 +0200 - 2026/07/10 10:30:00 +0200"
            }
        }

        `when`("the incident started on the day before") {
            val incident = incident("2026-07-09T21:00:00Z", "2026-07-09T23:15:00Z")

            then("it starts at the midnight of the day") {
                incident.spanOnDay(day) shouldBe "2026/07/10 00:00:00 +0200 - 2026/07/10 01:15:00 +0200"
            }

            then("on the day before, it ends at the next midnight") {
                incident.spanOnDay(day.minusDays(1)) shouldBe
                    "2026/07/09 23:00:00 +0200 - 2026/07/10 00:00:00 +0200"
            }
        }

        `when`("the incident covers the whole day") {
            val incident = incident("2026-07-09T12:00:00Z", "2026-07-12T12:00:00Z")

            then("it's shown from midnight to midnight") {
                incident.spanOnDay(day) shouldBe "2026/07/10 00:00:00 +0200 - 2026/07/11 00:00:00 +0200"
            }
        }

        `when`("the incident is still ongoing on a past day") {
            val incident = incident("2026-07-10T20:00:00Z", endedAt = null)

            then("it ends at the next midnight, as it's only ongoing today") {
                incident.spanOnDay(day) shouldBe "2026/07/10 22:00:00 +0200 - 2026/07/11 00:00:00 +0200"
            }
        }

        `when`("the incident is ongoing today") {
            val startedAt = OffsetDateTime.now()
            val incident = incident(startedAt, endedAt = null)

            then("it's ongoing") {
                incident.spanOnDay(startedAt.toSystemZone().toLocalDate()) shouldBe
                    "${startedAt.toSystemZone().toDateTimeStringWithZone()} - ongoing"
            }
        }

        `when`("the day is the one the clocks are turned back on") {
            val incident = incident("2026-10-24T12:00:00Z", "2026-10-26T12:00:00Z")

            then("its midnights are in different offsets") {
                incident.spanOnDay(LocalDate.parse("2026-10-25")) shouldBe
                    "2026/10/25 00:00:00 +0200 - 2026/10/26 00:00:00 +0100"
            }
        }
    }

    given("the tooltip of a day of the history") {
        val day = LocalDate.parse("2026-07-10")

        `when`("the day has more incidents than the tooltip lists, in no particular order") {
            val sevenIncidents = (0..6).map { index ->
                val start = OffsetDateTime.parse("2026-07-10T00:00:00Z").plusHours(index.toLong())
                incident(start, start.plusMinutes(10))
            }
            val history = StatusHistoryDto(
                date = day,
                outageCnt = sevenIncidents.size,
                downtimeSeconds = 4200,
                incidents = listOf(4, 0, 6, 2, 5, 1, 3).map { sevenIncidents[it] },
            )
            val tooltip = history.tooltipHtml()

            then("it lists only the latest ones, by the time they started") {
                tooltip.split("<li>").size - 1 shouldBe 5
                sevenIncidents.drop(2).forEach { tooltip shouldContain it.spanOnDay(day) }
                sevenIncidents.take(2).forEach { tooltip shouldNotContain it.spanOnDay(day) }
            }
        }
    }
})
