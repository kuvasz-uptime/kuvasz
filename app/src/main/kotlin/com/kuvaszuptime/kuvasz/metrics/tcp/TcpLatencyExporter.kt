package com.kuvaszuptime.kuvasz.metrics.tcp

import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.tcp.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.TcpMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.tcp-latest-latency", value = StringUtils.TRUE),
)
class TcpLatencyExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val metricsRepository: TcpMetricsLogRepository,
    monitorRepository: SharedMonitorRepository,
) : GaugeExporter<Int, TcpMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.TCP,
) {

    companion object {
        private const val MONITOR_LATENCY = "tcp.latency.latest.milliseconds"
    }

    override val meterName = MONITOR_LATENCY

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToTcpMonitorUpEvents { event ->
            val latency = event.latencyInMs ?: return@subscribeToTcpMonitorUpEvents
            logger.debug("Updating latency for monitor with ID: ${event.monitor.id} to $latency")
            upsertMeter(event.monitor.numericMonitorId(), latency)
        }
    }

    override fun transform(valueSource: Int): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: TcpMonitorRecord): Int? =
        metricsRepository.fetchLastByMonitorId(monitor.id)?.latencyInMs

    override fun filterCondition(monitor: TcpMonitorRecord): Boolean = monitor.enabled
}
