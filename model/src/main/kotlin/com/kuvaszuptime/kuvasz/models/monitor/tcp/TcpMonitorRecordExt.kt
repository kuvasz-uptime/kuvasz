package com.kuvaszuptime.kuvasz.models.monitor.tcp

import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID

fun TcpMonitorRecord.monitorId() = MonitorID(MonitorType.TCP, name)
fun TcpMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.TCP, id)
