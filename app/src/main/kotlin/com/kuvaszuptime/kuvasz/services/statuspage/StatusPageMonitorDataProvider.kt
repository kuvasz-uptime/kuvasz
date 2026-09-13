package com.kuvaszuptime.kuvasz.services.statuspage

import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import java.time.Duration

interface StatusPageMonitorDataProvider {

    /**
     * Fetches the data of enabled monitors for the status page. Every implementation is responsible for handling
     * only the monitor types it supports.
     *
     * [monitorIds] and [categories] are additive selectors: the result is the union of the monitors referenced
     * explicitly and the ones belonging to one of the categories. Both being null means no restriction at all,
     * which is how the default status page collects every enabled monitor.
     */
    fun getStatusPageDataOfEnabledMonitors(
        period: Duration,
        monitorIds: List<MonitorID>?,
        categories: List<String>?,
    ): List<StatusPageMonitorDetailsDto>
}
