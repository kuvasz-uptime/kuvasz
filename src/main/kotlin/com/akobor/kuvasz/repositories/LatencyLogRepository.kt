package com.akobor.kuvasz.repositories

import com.akobor.kuvasz.tables.daos.LatencyLogDao
import com.akobor.kuvasz.tables.pojos.LatencyLogPojo
import org.jooq.Configuration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LatencyLogRepository @Inject constructor(jooqConfig: Configuration) : LatencyLogDao(jooqConfig) {

    fun insertLatencyForMonitor(monitorId: Int, latency: Int) {
        insert(
            LatencyLogPojo()
                .setMonitorId(monitorId)
                .setLatency(latency)
        )
    }
}
