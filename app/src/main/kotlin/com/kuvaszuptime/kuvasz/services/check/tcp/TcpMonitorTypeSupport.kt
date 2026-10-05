package com.kuvaszuptime.kuvasz.services.check.tcp

import com.kuvaszuptime.kuvasz.models.dto.monitor.TcpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.monitor.tcp.TcpMonitorCreator
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.services.monitor.MetricsHistoryMonitorTypeSupport
import com.kuvaszuptime.kuvasz.services.proxy.ProxyRegistry
import com.kuvaszuptime.kuvasz.util.nullIfBlank
import jakarta.inject.Singleton

@Singleton
class TcpMonitorTypeSupport(
    override val repository: TcpMonitorRepository,
    override val metricsLogRepository: TcpMetricsLogRepository,
    private val proxyRegistry: ProxyRegistry,
) : MetricsHistoryMonitorTypeSupport<TcpMonitorCreator, TcpMonitorRecord, TcpMonitorDetailsDto>() {

    /**
     * Only a proxy that is newly set has to be configured, the same way as for HTTP monitors: a monitor keeps its proxy
     * even after that was removed from the config, so it stays editable, and its checks can report the dangling
     * reference instead of silently connecting directly.
     */
    override fun beforeUpsert(previous: TcpMonitorRecord?, toUpsert: TcpMonitorRecord) {
        val proxy = toUpsert.proxy.nullIfBlank()
        if (proxy != null && proxy != previous?.proxy) {
            proxyRegistry.requireConfiguredProxy(proxy)
        }
    }
}
