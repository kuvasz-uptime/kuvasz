package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.models.MonitorType

data class MonitorIDWithName(
    val type: MonitorType,
    val id: Long,
    val name: String,
) {
    val monitorId: MonitorID = MonitorID(type, name)
    val numericMonitorId: NumericMonitorID = NumericMonitorID(type, id)
}
