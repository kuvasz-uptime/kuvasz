package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.PagerdutyKeyUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import io.micronaut.http.client.annotation.Client

@Client("/api/v1/monitors")
interface MonitorClient : MonitorOperations {
    override fun getMonitorDetails(monitorId: Int): MonitorDetailsDto

    override fun getMonitorsWithDetails(enabledOnly: Boolean?): List<MonitorDetailsDto>

    override fun createMonitor(monitor: MonitorCreateDto): MonitorDto

    override fun deleteMonitor(monitorId: Int)

    override fun updateMonitor(monitorId: Int, monitorUpdateDto: MonitorUpdateDto): MonitorDto

    override fun upsertPagerdutyIntegrationKey(monitorId: Int, upsertDto: PagerdutyKeyUpdateDto): MonitorDto

    override fun deletePagerdutyIntegrationKey(monitorId: Int)

    override fun getUptimeEvents(monitorId: Int): List<UptimeEventDto>

    override fun getSSLEvents(monitorId: Int): List<SSLEventDto>
}
