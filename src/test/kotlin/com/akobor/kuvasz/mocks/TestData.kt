package com.akobor.kuvasz.mocks

import com.akobor.kuvasz.repositories.MonitorRepository
import com.akobor.kuvasz.tables.pojos.MonitorPojo

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
    repository.insert(monitor)
    return monitor
}
