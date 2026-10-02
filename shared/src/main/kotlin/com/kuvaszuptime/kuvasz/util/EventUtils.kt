package com.kuvaszuptime.kuvasz.util

import java.time.OffsetDateTime

/**
 * Calculates the duration of an event depending on the state of the monitor, its start date, end date and last update
 *
 * @param isMonitorEnabled Whether the check is enabled (it's not necessary the monitor, could be the SSL check)
 * @param startedAt The start date of the event
 * @param endedAt The end date of the event, if it's already ended
 * @param updatedAt The last known update of the event
 * @param now The timestamp to treat as the current one when the event is still ongoing. Callers that summarize
 * multiple events should pass the very same instant for all of them, otherwise the individual durations drift apart.
 *
 * @return The effective duration of the event in seconds
 */
fun getDurationOfEvent(
    isMonitorEnabled: Boolean,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    updatedAt: OffsetDateTime,
    now: OffsetDateTime = getCurrentTimestamp(),
): Long = startedAt.diffToDuration(getEffectiveEndOfEvent(isMonitorEnabled, endedAt, updatedAt, now)).inWholeSeconds

/**
 * The last instant when an event was effective: its end date, or if it's still ongoing, the actual timestamp for an
 * enabled monitor, and the last update for a paused one, because that is the LAST KNOWN date when the state was
 * effective
 */
fun getEffectiveEndOfEvent(
    isMonitorEnabled: Boolean,
    endedAt: OffsetDateTime?,
    updatedAt: OffsetDateTime,
    now: OffsetDateTime,
): OffsetDateTime = endedAt ?: if (isMonitorEnabled) now else updatedAt
