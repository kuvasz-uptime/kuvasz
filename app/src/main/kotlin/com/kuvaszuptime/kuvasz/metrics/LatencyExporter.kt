package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.events.MonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.LatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.latest-latency", value = StringUtils.TRUE),
)
class LatencyExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val latencyLogRepository: LatencyLogRepository,
    monitorRepository: MonitorRepository,
) : GaugeExporter<Int>(meterRegistry, eventDispatcher, monitorRepository) {

    companion object {
        private const val MONITOR_LATENCY = "monitor.latency.latest.milliseconds"
    }

    override val meterName = MONITOR_LATENCY

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            event.handle()
        }
    }

    private fun MonitorUpEvent.handle() {
        logger.debug("Updating latency for monitor with ID: ${monitor.id} to $latency")
        upsertMeter(monitor.id, latency)
    }

    override fun transform(valueSource: Int): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: MonitorRecord): Int? =
        latencyLogRepository.fetchLastByMonitorId(monitor.id)?.latencyInMs

    override fun filterCondition(monitor: MonitorRecord): Boolean = monitor.enabled
}
