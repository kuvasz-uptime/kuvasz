package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class MaintenanceWindowCalculatorTest : StringSpec({

    val calculator = MaintenanceWindowCalculator()
    val day = LocalDate.of(2026, 6, 23)

    // Builds an OffsetDateTime in the system default zone, so it lines up with how the calculator evaluates cron
    fun at(date: LocalDate, hour: Int, minute: Int = 0): OffsetDateTime =
        date.atTime(hour, minute).atZone(ZoneId.systemDefault()).toOffsetDateTime()

    fun manualWindow(enabled: Boolean = true) = MaintenanceWindowRecord()
        .setName("manual")
        .setEnabled(enabled)

    fun cronWindow(cron: String = "0 2 * * *", duration: String = "PT1H", enabled: Boolean = true) =
        MaintenanceWindowRecord()
            .setName("cron")
            .setEnabled(enabled)
            .setCron(cron)
            .setDuration(duration)

    fun singleWindow(start: OffsetDateTime, duration: String = "PT2H", enabled: Boolean = true) =
        MaintenanceWindowRecord()
            .setName("single")
            .setEnabled(enabled)
            .setStart(start)
            .setDuration(duration)

    // Manual windows

    "a manual window is active when enabled and inactive when disabled" {
        calculator.isActive(manualWindow(enabled = true), at(day, 12)) shouldBe true
        calculator.isActive(manualWindow(enabled = false), at(day, 12)) shouldBe false
    }

    "a manual window has no concrete interval" {
        calculator.currentInterval(manualWindow(), at(day, 12)).shouldBeNull()
        calculator.nextInterval(manualWindow(), at(day, 12)).shouldBeNull()
        calculator.occurrencesBetween(manualWindow(), at(day, 0), at(day.plusDays(1), 0)).shouldBeEmpty()
    }

    // Cron windows

    "a cron window is active inside its interval and inactive outside" {
        val window = cronWindow() // 02:00 daily, lasts 1 hour

        calculator.isActive(window, at(day, 2, 30)) shouldBe true
        calculator.isActive(window, at(day, 2, 0)) shouldBe true
        calculator.isActive(window, at(day, 3, 0)) shouldBe false // end is exclusive
        calculator.isActive(window, at(day, 1, 59)) shouldBe false
    }

    "a disabled cron window is never active and has no current interval" {
        val window = cronWindow(enabled = false)

        calculator.isActive(window, at(day, 2, 30)) shouldBe false
        calculator.currentInterval(window, at(day, 2, 30)).shouldBeNull()
        calculator.nextInterval(window, at(day, 2, 30)).shouldBeNull()
    }

    "currentInterval of an active cron window returns the running interval" {
        val interval = calculator.currentInterval(cronWindow(), at(day, 2, 30)).shouldNotBeNull()

        interval.start shouldBe at(day, 2)
        interval.end shouldBe at(day, 3)
    }

    "nextInterval of a cron window returns the next upcoming occurrence" {
        val interval = calculator.nextInterval(cronWindow(), at(day, 5)).shouldNotBeNull()

        interval.start shouldBe at(day.plusDays(1), 2)
        interval.end shouldBe at(day.plusDays(1), 3)
    }

    "currentOrNextInterval returns the current interval when active, otherwise the next one" {
        val window = cronWindow()

        calculator.currentOrNextInterval(window, at(day, 2, 30)).shouldNotBeNull().start shouldBe at(day, 2)
        calculator.currentOrNextInterval(window, at(day, 5)).shouldNotBeNull().start shouldBe at(day.plusDays(1), 2)
    }

    "occurrencesBetween returns all cron occurrences overlapping the range" {
        val occurrences = calculator.occurrencesBetween(
            cronWindow(),
            from = at(day, 0),
            to = at(day.plusDays(3), 0),
        )

        occurrences shouldHaveSize 3
        occurrences.map { it.start } shouldBe listOf(
            at(day, 2),
            at(day.plusDays(1), 2),
            at(day.plusDays(2), 2),
        )
    }

    "occurrencesBetween includes an occurrence that started before the range but still overlaps it" {
        val occurrences = calculator.occurrencesBetween(
            cronWindow(),
            from = at(day, 2, 30), // we are 30 minutes into the 02:00-03:00 window
            to = at(day, 4),
        )

        occurrences shouldHaveSize 1
        occurrences.single().start shouldBe at(day, 2)
    }

    // Cron extensions (#, L) are supported by Micronaut's CronExpression and must flow through the calculator

    "a cron window using the 'L' extension fires on the last day of the month" {
        // 00:00 on the last day of the month, lasting 1 hour. June 2026 ends on the 30th.
        val window = cronWindow(cron = "0 0 L * *")
        val lastDayOfJune = LocalDate.of(2026, 6, 30)

        val interval = calculator.nextInterval(window, at(day, 5)).shouldNotBeNull()
        interval.start shouldBe at(lastDayOfJune, 0)
        interval.end shouldBe at(lastDayOfJune, 1)

        calculator.isActive(window, at(lastDayOfJune, 0, 30)) shouldBe true
        calculator.isActive(window, at(day, 0, 30)) shouldBe false
    }

    "a cron window using the '#' extension fires on the nth weekday of the month" {
        // 00:00 on the first Monday of the month. Relative to 2026-06-23 that is 2026-07-06.
        val window = cronWindow(cron = "0 0 * * MON#1")
        val firstMondayOfJuly = LocalDate.of(2026, 7, 6)

        val interval = calculator.nextInterval(window, at(day, 5)).shouldNotBeNull()
        interval.start shouldBe at(firstMondayOfJuly, 0)
        interval.end shouldBe at(firstMondayOfJuly, 1)

        calculator.isActive(window, at(firstMondayOfJuly, 0, 30)) shouldBe true
    }

    // Single windows

    "a single window is active only within its one-shot interval" {
        val window = singleWindow(start = at(day, 10)) // 10:00, lasts 2 hours

        calculator.isActive(window, at(day, 11)) shouldBe true
        calculator.isActive(window, at(day, 10)) shouldBe true
        calculator.isActive(window, at(day, 12)) shouldBe false // end is exclusive
        calculator.isActive(window, at(day, 9, 59)) shouldBe false
    }

    "a single window in the past is not active and has no next interval" {
        val window = singleWindow(start = at(day.minusDays(2), 10))

        calculator.isActive(window, at(day, 11)) shouldBe false
        calculator.nextInterval(window, at(day, 11)).shouldBeNull()
    }

    "nextInterval of a future single window returns its only interval" {
        val window = singleWindow(start = at(day.plusDays(1), 10))
        val interval = calculator.nextInterval(window, at(day, 11)).shouldNotBeNull()

        interval.start shouldBe at(day.plusDays(1), 10)
        interval.end shouldBe at(day.plusDays(1), 12)
    }

    "occurrencesBetween returns the single window's interval when it overlaps the range" {
        val window = singleWindow(start = at(day, 10)) // [10:00, 12:00)

        val overlapping = calculator.occurrencesBetween(window, at(day, 0), at(day.plusDays(1), 0))
        overlapping shouldHaveSize 1
        overlapping.single().start shouldBe at(day, 10)

        calculator.occurrencesBetween(window, at(day, 13), at(day, 14)).shouldBeEmpty()
    }

    // Disabled / invalid windows across the interval helpers

    "occurrencesBetween returns nothing for a disabled window" {
        calculator.occurrencesBetween(
            cronWindow(enabled = false),
            at(day, 0),
            at(day.plusDays(1), 0),
        ).shouldBeEmpty()
    }

    "occurrencesBetween returns nothing for an invalid cron expression" {
        calculator.occurrencesBetween(
            cronWindow(cron = "not-a-cron"),
            at(day, 0),
            at(day.plusDays(1), 0),
        ).shouldBeEmpty()
    }

    "nextInterval returns null for an invalid cron expression" {
        calculator.nextInterval(cronWindow(cron = "not-a-cron"), at(day, 5)).shouldBeNull()
    }

    "a manual window that happens to carry a duration still has no concrete interval" {
        val window = MaintenanceWindowRecord()
            .setName("manual-with-duration")
            .setEnabled(true)
            .setDuration("PT1H")

        calculator.currentInterval(window, at(day, 12)).shouldBeNull()
        calculator.nextInterval(window, at(day, 12)).shouldBeNull()
        calculator.occurrencesBetween(window, at(day, 0), at(day.plusDays(1), 0)).shouldBeEmpty()
    }

    "the helpers fall back to the current time when no instant is provided" {
        // Manual windows are deterministic regardless of the wall-clock, so they exercise the default-now overloads
        calculator.isActive(manualWindow(enabled = true)) shouldBe true
        calculator.isActive(manualWindow(enabled = false)) shouldBe false
        calculator.currentInterval(manualWindow(enabled = true)).shouldBeNull()
        calculator.nextInterval(manualWindow(enabled = true)).shouldBeNull()
        calculator.currentOrNextInterval(manualWindow(enabled = false)).shouldBeNull()
    }

    // Defensive parsing

    "an invalid cron expression is treated as never active" {
        val window = cronWindow(cron = "not-a-cron")

        calculator.isActive(window, at(day, 2, 30)) shouldBe false
        calculator.currentInterval(window, at(day, 2, 30)).shouldBeNull()
    }

    "an invalid duration is treated as never active" {
        val window = cronWindow(duration = "not-a-duration")

        calculator.isActive(window, at(day, 2, 30)) shouldBe false
    }
})
