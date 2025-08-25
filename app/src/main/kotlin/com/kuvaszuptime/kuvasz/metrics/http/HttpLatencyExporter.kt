package com.kuvaszuptime.kuvasz.metrics.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.events.HttpMonitorUpEvent
import com.kuvaszuptime.kuvasz.repositories.HttpLatencyLogRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.http-latest-latency", value = StringUtils.TRUE),
)
class HttpLatencyExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val latencyLogRepository: HttpLatencyLogRepository,
    monitorRepository: HttpMonitorRepository,
) : HttpGaugeExporter<Int>(meterRegistry, eventDispatcher, monitorRepository) {

    companion object {
        private const val MONITOR_LATENCY = "http.latency.latest.milliseconds"
    }

    override val meterName = MONITOR_LATENCY

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToHttpMonitorUpEvents { event ->
            event.handle()
        }
    }

    private fun HttpMonitorUpEvent.handle() {
        logger.debug("Updating latency for monitor with ID: ${monitor.id} to $latency")
        upsertMeter(monitor.id, latency)
    }

    override fun transform(valueSource: Int): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: HttpMonitorRecord): Int? =
        latencyLogRepository.fetchLastByMonitorId(monitor.id)?.latencyInMs

    override fun filterCondition(monitor: HttpMonitorRecord): Boolean = monitor.enabled
}
