package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.monitor.dns.DnsMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.tcp.TcpMonitorExportDto
import io.micronaut.core.annotation.Introspected

@Introspected
data class MonitorImportDto(
    val httpMonitors: List<HttpMonitorExportDto>? = null,
    val pushMonitors: List<PushMonitorExportDto>? = null,
    val icmpMonitors: List<IcmpMonitorExportDto>? = null,
    val tcpMonitors: List<TcpMonitorExportDto>? = null,
    val dnsMonitors: List<DnsMonitorExportDto>? = null,
)
