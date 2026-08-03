package com.kuvaszuptime.kuvasz.metrics.dns

import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.dns.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.DnsMetricsLogRepository
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
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.dns-latest-latency", value = StringUtils.TRUE),
)
class DnsLatencyExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val metricsRepository: DnsMetricsLogRepository,
    monitorRepository: SharedMonitorRepository,
) : GaugeExporter<Int, DnsMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DNS,
) {

    companion object {
        private const val MONITOR_LATENCY = "dns.latency.latest.milliseconds"
    }

    override val meterName = MONITOR_LATENCY

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToDnsMonitorUpEvents { event ->
            updateLatency(event.monitor, event.latencyInMs)
        }
        // A monitor that resolved but breached its latency threshold is DOWN yet still carries a real
        // measurement, so the gauge stays in sync with the metrics log instead of freezing on the last UP.
        eventDispatcher.subscribeToDnsMonitorDownEvents { event ->
            updateLatency(event.monitor, event.latencyInMs)
        }
    }

    private fun updateLatency(monitor: DnsMonitorRecord, latencyInMs: Int?) {
        val latency = latencyInMs ?: return
        logger.debug("Updating latency for monitor with ID: ${monitor.id} to $latency")
        upsertMeter(monitor.numericMonitorId(), latency)
    }

    override fun transform(valueSource: Int): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: DnsMonitorRecord): Int? =
        metricsRepository.fetchLastByMonitorId(monitor.id)?.latencyInMs

    override fun filterCondition(monitor: DnsMonitorRecord): Boolean = monitor.enabled
}
