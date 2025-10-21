package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import java.time.Duration

interface StatusPageMonitorDataProvider {

    /**
     * Fetches the data of enabled monitors for the status page. Every implementation is responsible for handling
     * only the monitor types it supports.
     */
    fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
    ): List<StatusPageMonitorDetailsDto>
}
