package com.kuvaszuptime.kuvasz.models.dto.import

import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import io.micronaut.core.annotation.Introspected

@Introspected
data class MonitorImportDto(
    val httpMonitors: List<HttpMonitorExportDto>? = null,
    val pushMonitors: List<PushMonitorExportDto>? = null,
    val icmpMonitors: List<IcmpMonitorExportDto>? = null,
)
