package com.kuvaszuptime.kuvasz.models.events

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.UptimeEventRecord
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.util.diffToDuration
import kotlin.time.Duration

sealed class UptimeMonitorEvent : MonitorEvent<MonitorRecord>() {
    abstract val previousEvent: UptimeEventRecord?

    abstract val uptimeStatus: UptimeStatus

    fun isUp() = uptimeStatus == UptimeStatus.UP

    fun wasUpWithThreshold(): Boolean = previousEvent?.status != UptimeStatus.DOWN && monitor.failureCountThreshold > 1

    fun statusNotEquals(previousEvent: UptimeEventRecord) = !statusEquals(previousEvent)

    fun getEndedEventDuration(): Duration? =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                previousEvent.startedAt.diffToDuration(dispatchedAt)
            } else {
                null
            }
        }

    fun runWhenStateChanges(toRun: (UptimeMonitorEvent) -> Unit) =
        previousEvent?.let { previousEvent ->
            if (statusNotEquals(previousEvent)) {
                toRun(this)
            }
        } ?: toRun(this)

    private fun statusEquals(previousEvent: UptimeEventRecord) = uptimeStatus == previousEvent.status
}
