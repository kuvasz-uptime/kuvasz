package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import io.micronaut.http.client.annotation.Client

@Client("/monitor")
interface MonitorClient : MonitorOperations {
    override fun getMonitor(monitorId: Int): MonitorPojo

    override fun getMonitors(enabledOnly: Boolean?): List<MonitorPojo>

    override fun getMonitorDetails(monitorId: Int): MonitorDetailsDto

    override fun getMonitorsWithDetails(enabledOnly: Boolean?): List<MonitorDetailsDto>

    override fun createMonitor(monitor: MonitorCreateDto): MonitorPojo

    override fun deleteMonitor(monitorId: Int)

    override fun updateMonitor(monitorId: Int, monitorUpdateDto: MonitorUpdateDto): MonitorPojo
}
