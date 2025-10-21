package com.kuvaszuptime.kuvasz.models.monitor

import com.kuvaszuptime.kuvasz.models.MonitorType

data class NumericMonitorID(
    val type: MonitorType,
    val id: Long,
)
