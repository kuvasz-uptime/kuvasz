package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.models.MonitorType

data class MonitorIDWithName(
    val type: MonitorType,
    val id: Long,
    val name: String,
) {
    val monitorId: MonitorID by lazy { MonitorID(type, name) }
    val numericMonitorId: NumericMonitorID by lazy { NumericMonitorID(type, id) }
}
