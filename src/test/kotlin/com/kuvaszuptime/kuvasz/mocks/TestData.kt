package com.kuvaszuptime.kuvasz.mocks

import com.kuvaszuptime.kuvasz.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.repositories.UptimeEventRepository
import com.kuvaszuptime.kuvasz.tables.pojos.MonitorPojo
import com.kuvaszuptime.kuvasz.tables.pojos.UptimeEventPojo
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import java.time.OffsetDateTime

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

fun createUptimeEventRecord(
    repository: UptimeEventRepository,
    monitorId: Int,
    status: UptimeStatus = UptimeStatus.UP,
    startedAt: OffsetDateTime,
    endedAt: OffsetDateTime?
) =
    repository.insert(
        UptimeEventPojo()
            .setMonitorId(monitorId)
            .setStatus(status)
            .setStartedAt(startedAt)
            .setUpdatedAt(endedAt ?: startedAt)
            .setEndedAt(endedAt)
    )
