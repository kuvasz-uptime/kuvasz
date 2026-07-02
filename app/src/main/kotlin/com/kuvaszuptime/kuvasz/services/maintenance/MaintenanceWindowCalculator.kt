package com.kuvaszuptime.kuvasz.services.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.scheduling.cron.CronExpression
import jakarta.inject.Singleton
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * A concrete, time-bounded occurrence of a maintenance window: it is active in the half-open
 * interval `[start, end)`.
 */
data class MaintenanceInterval(
    val start: OffsetDateTime,
    val end: OffsetDateTime,
) {
    fun contains(instant: OffsetDateTime): Boolean = !instant.isBefore(start) && instant.isBefore(end)

    fun overlaps(from: OffsetDateTime, to: OffsetDateTime): Boolean = start.isBefore(to) && end.isAfter(from)
}

/**
 * Pure, side-effect-free calculator that answers scheduling questions about a maintenance window.
 *
 * Cron expressions are evaluated in the JVM's default time zone (which honors a `TZ`
 * environment variable), consistent with Micronaut's own `@Scheduled` cron handling and with [getCurrentTimestamp].
 * `start` is an absolute timestamp, so it is zone-independent.
 *
 * There are three kinds of windows:
 * - **manual**: both `cron` and `start` are `null`. It is active purely based on `enabled` and has no time interval.
 * - **cron**: recurring schedule defined by `cron` + `duration`.
 * - **single**: one-shot schedule defined by `start` + `duration`.
 */
@Singleton
class MaintenanceWindowCalculator {

    /**
     * Whether the window is currently active at the given instant.
     */
    fun isActive(window: MaintenanceWindowRecord, now: OffsetDateTime = getCurrentTimestamp()): Boolean {
        if (window.enabled != true) return false
        return if (window.isManual()) {
            // A manual window is active solely based on its enabled flag
            true
        } else {
            currentInterval(window, now) != null
        }
    }

    /**
     * The interval that is currently active (i.e. contains [now]), or `null` if the window is not active right now.
     * Manual windows never have a concrete interval, so this returns `null` for them even when they are active.
     */
    fun currentInterval(
        window: MaintenanceWindowRecord,
        now: OffsetDateTime = getCurrentTimestamp(),
    ): MaintenanceInterval? {
        val duration = window.activeDuration() ?: return null
        val cron = window.cron
        val start = window.start

        return when {
            cron != null -> parseCron(cron, window.name)?.let { expr ->
                // A cron window can only be active now if it last fired within the last `duration`. The first fire
                // strictly after (now - duration) is therefore the only candidate that could still be running.
                expr.nextTimeAfter(now.minus(duration).toSchedulingZone())
                    .takeIf { !it.isAfter(now.toSchedulingZone()) }
                    ?.let { MaintenanceInterval(it.toOffsetDateTime(), it.plus(duration).toOffsetDateTime()) }
            }

            start != null ->
                MaintenanceInterval(start, start.plus(duration)).takeIf { it.contains(now) }

            else -> null
        }
    }

    /**
     * The next interval that starts strictly after [now], or `null` if there is none (manual windows, or single
     * windows whose start is already in the past).
     */
    fun nextInterval(
        window: MaintenanceWindowRecord,
        now: OffsetDateTime = getCurrentTimestamp(),
    ): MaintenanceInterval? {
        val duration = window.activeDuration() ?: return null
        val cron = window.cron
        val start = window.start

        return when {
            cron != null -> parseCron(cron, window.name)?.let { expr ->
                val nextStart = expr.nextTimeAfter(now.toSchedulingZone())
                MaintenanceInterval(nextStart.toOffsetDateTime(), nextStart.plus(duration).toOffsetDateTime())
            }

            start != null ->
                MaintenanceInterval(start, start.plus(duration)).takeIf { start.isAfter(now) }

            else -> null
        }
    }

    /**
     * The current interval if the window is active right now, otherwise the next upcoming interval. Useful for
     * scheduling and for the "active or starting soon" lookahead on status pages.
     */
    fun currentOrNextInterval(
        window: MaintenanceWindowRecord,
        now: OffsetDateTime = getCurrentTimestamp(),
    ): MaintenanceInterval? = currentInterval(window, now) ?: nextInterval(window, now)

    /**
     * All concrete intervals that overlap the `[from, to)` range, used to reconstruct historical occurrences (e.g. for
     * per-day maintenance counts on status pages). Manual windows have no time interval, so they never contribute.
     */
    fun occurrencesBetween(
        window: MaintenanceWindowRecord,
        from: OffsetDateTime,
        to: OffsetDateTime,
    ): List<MaintenanceInterval> {
        val duration = window.activeDuration() ?: return emptyList()
        val cron = window.cron
        val start = window.start

        return when {
            cron != null -> parseCron(cron, window.name)?.let { expr ->
                val occurrences = mutableListOf<MaintenanceInterval>()
                // Start scanning one duration before `from` so an occurrence that started earlier but still overlaps
                // the range is included as well.
                var cursor = expr.nextTimeAfter(from.minus(duration).toSchedulingZone())
                while (cursor.toOffsetDateTime().isBefore(to)) {
                    val occurrenceStart = cursor.toOffsetDateTime()
                    val interval = MaintenanceInterval(occurrenceStart, cursor.plus(duration).toOffsetDateTime())
                    if (interval.overlaps(from, to)) {
                        occurrences.add(interval)
                    }
                    cursor = expr.nextTimeAfter(cursor)
                }
                occurrences
            }.orEmpty()

            start != null ->
                listOfNotNull(
                    MaintenanceInterval(start, start.plus(duration)).takeIf { interval -> interval.overlaps(from, to) }
                )

            else -> emptyList()
        }
    }

    private fun MaintenanceWindowRecord.isManual(): Boolean = cron == null && start == null

    private fun MaintenanceWindowRecord.activeDuration(): Duration? =
        parseDuration()?.takeIf { enabled == true }

    private fun MaintenanceWindowRecord.parseDuration(): Duration? =
        duration?.let { raw ->
            runCatching { Duration.parse(raw) }
                .onFailure { ex -> logger.warn("Invalid duration '$raw' on window '$name': ${ex.message}") }
                .getOrNull()
        }

    private fun parseCron(rawCron: String, windowName: String): CronExpression? =
        runCatching { CronExpression.create(rawCron) }
            .onFailure { ex -> logger.warn("Invalid cron '$rawCron' on window '$windowName': ${ex.message}") }
            .getOrNull()

    private fun OffsetDateTime.toSchedulingZone() = atZoneSameInstant(ZoneId.systemDefault())

    companion object {
        private val logger = loggerFor<MaintenanceWindowCalculator>()
    }
}
