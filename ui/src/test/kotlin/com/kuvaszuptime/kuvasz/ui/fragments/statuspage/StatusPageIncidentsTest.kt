package com.kuvaszuptime.kuvasz.ui.fragments.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.InvestigationStatus
import com.kuvaszuptime.kuvasz.models.dto.incident.IncidentStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusHistoryDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageHttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageIncidentNoteDto
import com.kuvaszuptime.kuvasz.models.statuspage.SystemStatus
import com.kuvaszuptime.kuvasz.models.theme.ThemeBase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.OffsetDateTime

private val BASE: OffsetDateTime = OffsetDateTime.parse("2026-07-10T08:00:00Z")

private fun incident(
    id: Long,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    notes: List<StatusPageIncidentNoteDto> = emptyList(),
) = StatusPageIncidentDto(
    id = id,
    startedAt = startedAt,
    endedAt = endedAt,
    status = if (endedAt == null) IncidentStatus.ONGOING else IncidentStatus.RESOLVED,
    notes = notes,
)

private fun note(content: String, status: InvestigationStatus, timestamp: OffsetDateTime) =
    StatusPageIncidentNoteDto(status = status, content = content, timestamp = timestamp)

private fun monitor(name: String, vararg days: List<StatusPageIncidentDto>) = StatusPageHttpMonitorDetailsDto(
    name = name,
    lastCheck = null,
    uptimeRatio = null,
    uptimeStatus = null,
    uptimeStatusHistory = days.mapIndexed { index, incidents ->
        StatusHistoryDto(
            date = BASE.toLocalDate().plusDays(index.toLong()),
            outageCnt = incidents.size,
            downtimeSeconds = 0,
            incidents = incidents,
        )
    },
    averageLatencyInMs = null,
)

private fun pageData(generatedAt: OffsetDateTime, vararg monitors: StatusPageHttpMonitorDetailsDto) =
    StatusPageDataDto(
        title = "Status",
        customLogoUrl = null,
        customFaviconUrl = null,
        systemStatus = SystemStatus.OPERATIONAL,
        generatedAt = generatedAt,
        monitors = monitors.toList(),
        categoryStatus = emptyList(),
        activeMaintenanceWindows = emptyList(),
        upcomingMaintenanceWindows = emptyList(),
        displayCategories = false,
        themeBase = ThemeBase.DEFAULT,
        historyDays = 30,
    )

class StatusPageIncidentsTest : BehaviorSpec({

    given("the public timeline of an incident") {
        val startedAt = BASE
        val endedAt = BASE.plusHours(2)

        `when`("it's ongoing, without any note") {
            val nodes = incident(1, startedAt, endedAt = null).publicTimelineNodes()

            then("it's only the start") {
                nodes shouldBe listOf(PublicTimelineNode.Started(startedAt))
            }
        }

        `when`("it's ongoing, with notes before and after its start") {
            val before = note("Degraded", InvestigationStatus.INVESTIGATING, startedAt.minusMinutes(5))
            val resolved = note("Fixed it", InvestigationStatus.RESOLVED, startedAt.plusMinutes(5))
            val identified = note("The DB", InvestigationStatus.IDENTIFIED, startedAt.plusMinutes(10))
            val nodes = incident(1, startedAt, endedAt = null, listOf(resolved, identified, before))
                .publicTimelineNodes()

            then("every note is a node of its own, the start never takes any, and the latest comes first") {
                nodes shouldBe listOf(
                    PublicTimelineNode.Note(identified),
                    PublicTimelineNode.Note(resolved),
                    PublicTimelineNode.Started(startedAt),
                    PublicTimelineNode.Note(before),
                )
            }
        }

        `when`("it's resolved, without any note") {
            val nodes = incident(1, startedAt, endedAt).publicTimelineNodes()

            then("it's the resolution, then the start") {
                nodes shouldBe listOf(
                    PublicTimelineNode.Resolved(endedAt, emptyList()),
                    PublicTimelineNode.Started(startedAt),
                )
            }
        }

        `when`("it has RESOLVED notes right before and right after its resolution") {
            val identified = note("The DB", InvestigationStatus.IDENTIFIED, startedAt.plusMinutes(30))
            val fixed = note("Fixed it", InvestigationStatus.RESOLVED, endedAt.minusMinutes(2))
            val verified = note("Verified", InvestigationStatus.RESOLVED, endedAt.minusMinutes(1))
            val postMortem = note("Post-mortem", InvestigationStatus.RESOLVED, endedAt.plusMinutes(10))
            val nodes = incident(1, startedAt, endedAt, listOf(postMortem, identified, verified, fixed))
                .publicTimelineNodes()

            then("the resolution carries them in their order, and the rest of the notes stand on their own") {
                nodes shouldBe listOf(
                    PublicTimelineNode.Resolved(endedAt, listOf(fixed, verified, postMortem)),
                    PublicTimelineNode.Note(identified),
                    PublicTimelineNode.Started(startedAt),
                )
            }
        }

        `when`("its RESOLVED notes are separated from the resolution by other notes") {
            val earlyFix = note("Hotfix", InvestigationStatus.RESOLVED, endedAt.minusMinutes(20))
            val monitoring = note("Monitoring", InvestigationStatus.IDENTIFIED, endedAt.minusMinutes(10))
            val relapse = note("Again", InvestigationStatus.INVESTIGATING, endedAt.plusMinutes(5))
            val lateFix = note("Fixed for good", InvestigationStatus.RESOLVED, endedAt.plusMinutes(10))
            val nodes = incident(1, startedAt, endedAt, listOf(earlyFix, monitoring, relapse, lateFix))
                .publicTimelineNodes()

            then("they aren't direct neighbours of the resolution, so they stay standalone") {
                nodes shouldBe listOf(
                    PublicTimelineNode.Note(lateFix),
                    PublicTimelineNode.Note(relapse),
                    PublicTimelineNode.Resolved(endedAt, emptyList()),
                    PublicTimelineNode.Note(monitoring),
                    PublicTimelineNode.Note(earlyFix),
                    PublicTimelineNode.Started(startedAt),
                )
            }
        }

        `when`("its notes are timed at the very same time as its start and its resolution") {
            val atStart = note("Looking", InvestigationStatus.INVESTIGATING, startedAt)
            val atEnd = note("Fixed it", InvestigationStatus.RESOLVED, endedAt)
            val nodes = incident(1, startedAt, endedAt, listOf(atEnd, atStart)).publicTimelineNodes()

            then("the incident's own nodes come first, so the note at the end is part of the resolution") {
                nodes shouldBe listOf(
                    PublicTimelineNode.Resolved(endedAt, listOf(atEnd)),
                    PublicTimelineNode.Note(atStart),
                    PublicTimelineNode.Started(startedAt),
                )
            }
        }

        `when`("its times are in different offsets") {
            val noteTime = OffsetDateTime.parse("2026-07-10T10:30:00+02:00")
            val note = note("Looking", InvestigationStatus.INVESTIGATING, noteTime)
            val nodes = incident(1, startedAt, endedAt = null, listOf(note)).publicTimelineNodes()

            then("they are compared as instants") {
                nodes shouldBe listOf(PublicTimelineNode.Note(note), PublicTimelineNode.Started(startedAt))
            }
        }
    }

    given("the incidents of a monitor's history") {

        `when`("an incident spans multiple days, and there are ongoing and resolved ones") {
            val older = incident(1, BASE, BASE.plusHours(1))
            val multiDay = incident(2, BASE.plusHours(20), BASE.plusHours(30))
            val ongoing = incident(3, BASE.plusHours(5), endedAt = null)
            val latest = incident(4, BASE.plusHours(40), BASE.plusHours(41))
            val monitor = monitor(
                "API",
                listOf(older, ongoing, multiDay),
                listOf(multiDay, latest, ongoing),
            )

            then("every incident is listed once, the ongoing ones first, then the latest ones") {
                monitor.incidentsOfHistory shouldBe listOf(ongoing, latest, multiDay, older)
            }
        }
    }

    given("the recent incidents of a status page") {
        val generatedAt = BASE.plusDays(2)
        val ongoing = incident(1, generatedAt.minusHours(1), endedAt = null)
        val recentlyResolved = incident(2, generatedAt.minusHours(3), generatedAt.minusHours(2))
        val resolvedADayAgo = incident(3, generatedAt.minusHours(25), generatedAt.minusHours(24))
        val longResolved = incident(4, generatedAt.minusDays(2), generatedAt.minusDays(2).plusHours(1))
        val zulu = monitor("zulu", listOf(ongoing, recentlyResolved))
        val alpha = monitor("Alpha", listOf(recentlyResolved.copy(id = 5)))
        val calm = monitor("calm", listOf(resolvedADayAgo, longResolved))
        val data = pageData(generatedAt, zulu, calm, alpha)

        `when`("the active incidents are collected") {
            val active = data.ongoingIncidentsByMonitor()

            then("only the ongoing ones are there, by their monitors") {
                active shouldBe listOf(zulu to listOf(ongoing))
            }
        }

        `when`("the resolved incidents are collected") {
            val resolved = data.recentlyResolvedIncidentsByMonitor()

            then("only the ones resolved within the last day are there, the monitors ordered by their names") {
                resolved shouldBe listOf(
                    alpha to listOf(recentlyResolved.copy(id = 5)),
                    zulu to listOf(recentlyResolved),
                )
            }
        }
    }

    given("the time span of an incident in the incidents modal") {
        // The tests run in Europe/Vienna, which is UTC+2 in July

        `when`("it's ongoing") {
            then("it ends with \"ongoing\"") {
                incident(1, BASE, endedAt = null).spanText() shouldBe "2026/07/10 10:00:00 - ongoing"
            }
        }

        `when`("it was resolved on the day it started") {
            then("only the time of the resolution is shown, with the duration") {
                incident(1, BASE, BASE.plusHours(2).plusMinutes(9)).spanText() shouldBe
                    "2026/07/10 10:00:00 - 12:09:00 (2 hours 9 minutes)"
            }
        }

        `when`("it was resolved on a later day") {
            then("the date of the resolution is shown too") {
                incident(1, BASE, BASE.plusHours(15)).spanText() shouldBe
                    "2026/07/10 10:00:00 - 2026/07/11 01:00:00 (15 hours)"
            }
        }
    }
})
