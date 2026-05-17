package com.kuvaszuptime.kuvasz.models.monitor.icmp

import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID

fun IcmpMonitorRecord.monitorId() = MonitorID(MonitorType.ICMP, name)
fun IcmpMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.ICMP, id)
