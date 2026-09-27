package com.kuvaszuptime.kuvasz.models.monitor.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorIDWithName
import com.kuvaszuptime.kuvasz.models.monitor.NumericMonitorID

fun DockerMonitorRecord.monitorId() = MonitorID(MonitorType.DOCKER, name)
fun DockerMonitorRecord.numericMonitorId() = NumericMonitorID(MonitorType.DOCKER, id)
fun DockerMonitorRecord.idWithName() = MonitorIDWithName(MonitorType.DOCKER, id, name)
