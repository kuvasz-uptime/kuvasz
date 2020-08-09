package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp

fun createMonitor(
    repository: MonitorRepository,
    id: Int = 99999,
    enabled: Boolean = true,
    uptimeCheckInterval: Int = 30000,
    monitorName: String = "testMonitor",
    url: String = "http://irrelevant.com"
): MonitorPojo {
    val monitor = MonitorPojo()
        .setId(id)
        .setName(monitorName)
        .setUptimeCheckInterval(uptimeCheckInterval)
        .setUrl(url)
        .setEnabled(enabled)
        .setCreatedAt(getCurrentTimestamp())
    repository.insert(monitor)
    return monitor
}
