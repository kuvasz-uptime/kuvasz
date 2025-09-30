package com.kuvaszuptime.kuvasz.models.monitor.push

import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID

fun PushMonitorRecord.monitorId() = MonitorID(MonitorType.PUSH, name)
fun PushMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.PUSH, id)
