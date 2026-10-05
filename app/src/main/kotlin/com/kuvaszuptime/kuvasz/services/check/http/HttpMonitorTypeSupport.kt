package com.kuvaszuptime.kuvasz.services.check.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.models.dto.monitor.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.MetricsHistoryMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import com.kuvaszuptime.kuvasz.util.nullIfBlank
import jakarta.inject.Singleton

@Singleton
class HttpMonitorTypeSupport(
    override val repository: HttpMonitorRepository,
    override val metricsLogRepository: HttpLatencyLogRepository,
    private val proxyRegistry: ProxyRegistry,
) : MetricsHistoryMonitorTypeSupport<HttpMonitorCreator, HttpMonitorRecord, HttpMonitorDetailsDto>() {

    /**
     * Only a proxy that is newly set has to be configured. A monitor keeps its proxy even after that was removed from
     * the config - a YAML monitor re-imported after a restart included -, so it stays editable, and its checks can
     * report the dangling reference instead of silently connecting directly.
     */
    override fun beforeUpsert(previous: HttpMonitorRecord?, toUpsert: HttpMonitorRecord) {
        val proxy = toUpsert.proxy.nullIfBlank()
        if (proxy != null && proxy != previous?.proxy) {
            proxyRegistry.requireConfiguredProxy(proxy)
        }
    }
}
