package com.kuvaszuptime.kuvasz.controllers.monitor

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.event.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import io.micronaut.http.client.annotation.Client
import java.time.Duration

@Client("/api/v2/http-monitors")
interface HttpMonitorClient : HttpMonitorOperations {
    override fun getMonitorDetails(monitorId: Long): HttpMonitorDetailsDto

    override fun getMonitorsWithDetails(
        enabled: Boolean?,
        uptimeStatus: List<UptimeStatus>?,
        sslStatus: List<SslStatus>?,
        sslCheckEnabled: Boolean?,
    ): List<HttpMonitorDetailsDto>

    override fun createMonitor(monitor: HttpMonitorCreateDto): HttpMonitorDto

    override fun deleteMonitor(monitorId: Long)

    override fun updateMonitor(monitorId: Long, updates: ObjectNode): HttpMonitorDto

    override fun getUptimeEvents(monitorId: Long): List<HttpUptimeEventDto>

    override fun getSSLEvents(monitorId: Long): List<SSLEventDto>

    override fun getMonitorStats(monitorId: Long, period: Duration?): HttpMonitorStatsDto

    override fun getMonitoringStats(period: Duration?): HttpMonitoringStatsDto
}
