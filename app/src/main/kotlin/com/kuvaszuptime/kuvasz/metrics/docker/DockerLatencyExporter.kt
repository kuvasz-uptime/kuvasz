package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.docker.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
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
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.docker-latest-latency", value = StringUtils.TRUE),
)
class DockerLatencyExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val metricsRepository: DockerMetricsLogRepository,
    monitorRepository: SharedMonitorRepository,
) : GaugeExporter<Int, DockerMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DOCKER,
) {

    companion object {
        private const val MONITOR_LATENCY = "docker.api.latency.latest.milliseconds"
    }

    override val meterName = MONITOR_LATENCY

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToDockerMonitorUpEvents { event ->
            updateLatency(event.monitor, event.latencyInMs)
        }
        // A DOWN monitor still carries a real measurement whenever the daemon answered at all - a stopped
        // container is reported by a perfectly healthy daemon - so the gauge stays in sync with the metrics log
        // instead of freezing on the last UP.
        eventDispatcher.subscribeToDockerMonitorDownEvents { event ->
            updateLatency(event.monitor, event.latencyInMs)
        }
    }

    private fun updateLatency(monitor: DockerMonitorRecord, latencyInMs: Int?) {
        val latency = latencyInMs ?: return
        logger.debug("Updating latency for monitor with ID: ${monitor.id} to $latency")
        upsertMeter(monitor.numericMonitorId(), latency)
    }

    override fun transform(valueSource: Int): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: DockerMonitorRecord): Int? =
        metricsRepository.fetchLastLatencyByMonitorId(monitor.id)

    override fun filterCondition(monitor: DockerMonitorRecord): Boolean = monitor.enabled
}
