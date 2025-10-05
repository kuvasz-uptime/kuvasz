package com.kuvaszuptime.kuvasz.services.monitor

import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import com.kuvaszuptime.kuvasz.models.monitor.http.monitorId
import com.kuvaszuptime.kuvasz.models.monitor.push.monitorId
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import jakarta.inject.Singleton

@Singleton
class SharedMonitorActions(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
) {
    fun getConfiguredMonitors(): List<MonitorID> =
        httpMonitorRepository.fetchAll().map { it.monitorId() }
            .plus(pushMonitorRepository.fetchAll().map { it.monitorId() })
}
