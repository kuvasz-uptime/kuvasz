package com.akobor.kuvasz

import com.akobor.kuvasz.repositories.MonitorRepository
import com.akobor.kuvasz.tables.pojos.MonitorPojo

fun createMonitor(repository: MonitorRepository): MonitorPojo{
    val monitor = MonitorPojo()
        .setId(99999)
        .setName("testMonitor")
        .setUptimeCheckInterval(30000)
        .setUrl("http://irrelevant.com")
        .setEnabled(true)
    repository.insert(monitor)
    return monitor
}
