package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.MonitorDetails
import io.micronaut.http.client.annotation.Client

@Client("/monitor")
interface MonitorClient : MonitorOperations {
    override fun getMonitor(monitorId: Int): MonitorDetails

    override fun getMonitors(enabledOnly: Boolean?): List<MonitorDetails>
}
