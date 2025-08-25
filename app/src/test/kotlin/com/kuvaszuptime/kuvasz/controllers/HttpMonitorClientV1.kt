package com.kuvaszuptime.kuvasz.controllers

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import io.micronaut.http.client.annotation.Client
import java.time.Duration

@Client("/api/v1/monitors")
interface HttpMonitorClientV1 : HttpMonitorOperationsV1 {
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
