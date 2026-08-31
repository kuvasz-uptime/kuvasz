package com.kuvaszuptime.kuvasz.services.check

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.PendingFailureRepository
import org.jooq.DSLContext

fun UptimeMonitorEvent.isDownNow(
    pendingFailureRepository: PendingFailureRepository,
    txCtx: DSLContext? = null,
): Boolean {
    if (uptimeStatus == UptimeStatus.UP) return false

    return if (wasUpWithThreshold()) {
        // Saving the pending failure in case the monitor has a > 1 failure count threshold
        val pendingFailures = pendingFailureRepository.createOrIncrement(monitor.id, txCtx).failureCount
        val reachedThreshold = pendingFailures >= monitor.failureCountThreshold
        if (reachedThreshold) {
            // In case we reached the threshold, we can delete the pending failure, because the monitor is already
            // considered down and we won't need to check it anymore until it goes up and then down again
            pendingFailureRepository.deleteByMonitorId(monitor.id, txCtx)
        }
        reachedThreshold
    } else true
}
