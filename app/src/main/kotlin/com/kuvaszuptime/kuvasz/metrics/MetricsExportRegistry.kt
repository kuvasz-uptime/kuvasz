package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * MetricsExportRegistry is responsible for initializing the available metrics exporters.
 */
@Requires(bean = MeterRegistry::class)
@Singleton
class MetricsExportRegistry(
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val httpMetricsExporters: List<MetricsExporter<HttpMonitorRecord>>,
    private val pushMetricsExporters: List<MetricsExporter<PushMonitorRecord>>,
    private val icmpMetricsExporters: List<MetricsExporter<IcmpMonitorRecord>>,
    private val tcpMetricsExporters: List<MetricsExporter<TcpMonitorRecord>>,
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(MetricsExportRegistry::class.java)
    }

    /**
     * Reads the actually available monitors from the database and initializes the metrics exporters with them
     */
    fun initialize() {
        val httpMonitors = httpMonitorRepository.fetchByEnabled(enabled = true)
        httpMetricsExporters.forEach { it.init(httpMonitors) }

        val pushMonitors = pushMonitorRepository.fetchByEnabled(enabled = true)
        pushMetricsExporters.forEach { it.init(pushMonitors) }

        val icmpMonitors = icmpMonitorRepository.fetchByEnabled(enabled = true)
        icmpMetricsExporters.forEach { it.init(icmpMonitors) }

        val tcpMonitors = tcpMonitorRepository.fetchByEnabled(enabled = true)
        tcpMetricsExporters.forEach { it.init(tcpMonitors) }
    }

    private fun <M : MonitorRecord> MetricsExporter<M>.init(monitors: List<M>) {
        logger.debug("Initializing exporter: ${this::class.java.simpleName} for ${monitors.size} monitors")
        initialize(monitors)
    }
}
