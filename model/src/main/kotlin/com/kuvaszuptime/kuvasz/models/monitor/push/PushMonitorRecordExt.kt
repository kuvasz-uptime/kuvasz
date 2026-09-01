package com.kuvaszuptime.kuvasz.models.monitor.push

import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorIDWithName
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID

fun PushMonitorRecord.monitorId() = MonitorID(MonitorType.PUSH, name)
fun PushMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.PUSH, id)
fun PushMonitorRecord.idWithName() = MonitorIDWithName(MonitorType.PUSH, id, name)

/**
 * The settings that the already recorded failures of a monitor were counted against: how often a heartbeat is
 * expected, how long it can be late, and how many missed ones are tolerated. Changing any of them invalidates the
 * failures recorded so far, everything else (e.g. the name or the integrations of the monitor) leaves them intact.
 */
fun PushMonitorRecord.affectsFailureCounting(existing: PushMonitorRecord): Boolean =
    heartbeatInterval != existing.heartbeatInterval ||
        gracePeriod != existing.gracePeriod ||
        failureCountThreshold != existing.failureCountThreshold
