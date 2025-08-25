package com.kuvaszuptime.kuvasz.metrics.http

import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.MeterDefinition
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicLong

/**
 * Base class for gauge exporters that provides common functionality for registering and updating gauges.
 * The INTERNAL_VAL is always a [Long] along with the [AtomicLong] as the METER_VAL.
 */
abstract class HttpGaugeExporter<SOURCE_VAL : Any>(
    private val meterRegistry: MeterRegistry,
    eventDispatcher: EventDispatcher,
    monitorRepository: HttpMonitorRepository,
) : BaseHttpMetricsExporter<SOURCE_VAL, Long, AtomicLong>(monitorRepository, meterRegistry, eventDispatcher) {

    override fun updateValue(existingValue: AtomicLong, newValue: Long) {
        existingValue.set(newValue)
    }

    override fun register(monitor: HttpMonitorRecord, initialValue: Long): MeterDefinition<AtomicLong> {
        logger.debug("Registering gauge for monitor with ID: ${monitor.id}")
        val value = AtomicLong(initialValue)
        val gauge = Gauge.builder(prefixedMeterName(), value) { it.toDouble() }
            .commonTags(name = monitor.name, target = monitor.url)
            .register(meterRegistry)

        return MeterDefinition(gauge.id, value)
    }

    /**
     * Adds common tags to the gauge builder
     */
    private fun Gauge.Builder<*>.commonTags(name: String, target: String): Gauge.Builder<*> = this
        .tag("target", target)
        .tag("name", name)
}
