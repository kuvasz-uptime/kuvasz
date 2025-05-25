package com.kuvaszuptime.kuvasz.controllers

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import io.micronaut.http.client.annotation.Client
import java.time.Duration

@Client("/api/v1/monitors")
interface MonitorClient : MonitorOperations {
    override fun getMonitorDetails(monitorId: Long): MonitorDetailsDto

    override fun getMonitorsWithDetails(enabledOnly: Boolean?): List<MonitorDetailsDto>

    override fun createMonitor(monitor: MonitorCreateDto): MonitorDto

    override fun deleteMonitor(monitorId: Long)

    override fun updateMonitor(monitorId: Long, updates: ObjectNode): MonitorDto

    override fun getUptimeEvents(monitorId: Long): List<UptimeEventDto>

    override fun getSSLEvents(monitorId: Long): List<SSLEventDto>

    override fun getMonitorStats(monitorId: Long, period: Duration?): MonitorStatsDto
}
