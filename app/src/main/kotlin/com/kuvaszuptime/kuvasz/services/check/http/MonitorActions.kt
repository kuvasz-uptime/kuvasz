package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import jakarta.inject.Singleton
import org.jooq.SortField

@Singleton
class MonitorActions(
    private val httpMonitorRepository: HttpMonitorRepository,
) {
    fun getConfiguredMonitors(sortedBy: SortField<*>? = null): List<MonitorID> =
        httpMonitorRepository.fetchAll(sortedBy).map { it.monitorId() }
}
