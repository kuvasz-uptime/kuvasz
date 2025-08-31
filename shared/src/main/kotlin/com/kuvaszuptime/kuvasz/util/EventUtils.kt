package com.kuvaszuptime.kuvasz.util

import java.time.OffsetDateTime

/**
 * Calculates the duration of an event depending on the state of the monitor, its start date, end date and last update
 *
 * @param isMonitorEnabled Whether the check is enabled (it's not necessary the monitor, could be the SSL check)
 * @param startedAt The start date of the event
 * @param endedAt The end date of the event, if it's already ended
 * @param updatedAt The last known update of the event
 *
 * @return The effective duration of the event in seconds
 */
fun getDurationOfEvent(
    isMonitorEnabled: Boolean,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?,
    updatedAt: OffsetDateTime,
): Long {
    val effectiveEndDate = if (isMonitorEnabled) {
        // If the monitor is active then we use either the end date of the event or the actual timestamp in
        // case of an ongoing event
        endedAt ?: getCurrentTimestamp()
    } else {
        // If the monitors is paused then we use either the end date of the event, or the last update of it,
        // because this is the LAST KNOWN date when the current state was effective
        endedAt ?: updatedAt
    }
    return startedAt.diffToDuration(effectiveEndDate).inWholeSeconds
}
