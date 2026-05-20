package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.http.safeDisplayUrl
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicLong

/**
 * Base class for gauge exporters that provides common functionality for registering and updating gauges.
 * The INTERNAL_VAL is always a [Long] along with the [AtomicLong] as the METER_VAL.
 */
abstract class GaugeExporter<SOURCE_VAL : Any, MONITOR : MonitorRecord>(
    private val meterRegistry: MeterRegistry,
    eventDispatcher: EventDispatcher,
    monitorRepository: SharedMonitorRepository,
    monitorType: MonitorType,
) : BaseHttpMetricsExporter<SOURCE_VAL, Long, AtomicLong, MONITOR>(
    monitorRepository,
    meterRegistry,
    eventDispatcher,
    monitorType,
) {

    override fun updateValue(existingValue: AtomicLong, newValue: Long) {
        existingValue.set(newValue)
    }

    override fun register(monitor: MONITOR, initialValue: Long): MeterDefinition<AtomicLong> {
        logger.debug("Registering gauge for monitor with ID: ${monitor.id}")
        val value = AtomicLong(initialValue)
        val gauge = Gauge
            .builder(prefixedMeterName(), value) { it.toDouble() }
            .nameTag(name = monitor.name)
        when (monitor) {
            is HttpMonitorRecord -> gauge.targetTag(monitor.safeDisplayUrl)
            is IcmpMonitorRecord -> gauge.targetTag(monitor.host)
        }

        return MeterDefinition(gauge.register(meterRegistry).id, value)
    }

    private fun Gauge.Builder<*>.nameTag(name: String): Gauge.Builder<*> = this.tag("name", name)
    private fun Gauge.Builder<*>.targetTag(target: String): Gauge.Builder<*> = this.tag("target", target)
}
