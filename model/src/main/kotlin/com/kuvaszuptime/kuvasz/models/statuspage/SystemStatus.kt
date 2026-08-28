package com.kuvaszuptime.kuvasz.models.statuspage

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto

enum class SystemStatus {
    OPERATIONAL,
    PARTIAL_OUTAGE,
    MAJOR_OUTAGE,
    PARTIAL_MAINTENANCE,
    MAINTENANCE,
    PENDING,
    ;

    companion object {
        /**
         * Calculates the aggregated status of the given monitors, either for a whole status page or for a subset of
         * its monitors (e.g. a category).
         */
        fun fromMonitors(monitors: List<StatusPageMonitorDetailsDto>): SystemStatus =
            if (monitors.isEmpty()) {
                PENDING
            } else {
                val monitorStatusMap = monitors.groupBy { it.uptimeStatus }
                val monitorCnt = monitors.size
                val upCnt = monitorStatusMap[UptimeStatus.UP]?.size ?: 0
                val downCnt = monitorStatusMap[UptimeStatus.DOWN]?.size ?: 0
                val maintenanceCnt = monitors.count { it.inMaintenance }
                when {
                    // Outages always take precedence over maintenance
                    downCnt == monitorCnt -> MAJOR_OUTAGE
                    upCnt > 0 && downCnt > 0 -> PARTIAL_OUTAGE
                    // Maintenance takes precedence over the operational/pending states
                    downCnt == 0 && maintenanceCnt == monitorCnt -> MAINTENANCE
                    downCnt == 0 && maintenanceCnt > 0 -> PARTIAL_MAINTENANCE
                    upCnt == monitorCnt -> OPERATIONAL
                    else -> PENDING
                }
            }
    }
}
