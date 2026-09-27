package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.http.safeDisplayUrl
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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
        monitor.gaugeTarget()?.let { gauge.targetTag(it) }

        return MeterDefinition(gauge.register(meterRegistry).id, value)
    }
}

/**
 * The same gauge, for a measurement that has to stay fractional. A container idling at a few tenths of a percent
 * would read as a flat zero through the [Long] one, so the CPU usage cannot go through it.
 */
abstract class DecimalGaugeExporter<SOURCE_VAL : Any, MONITOR : MonitorRecord>(
    private val meterRegistry: MeterRegistry,
    eventDispatcher: EventDispatcher,
    monitorRepository: SharedMonitorRepository,
    monitorType: MonitorType,
) : BaseHttpMetricsExporter<SOURCE_VAL, Double, AtomicReference<Double>, MONITOR>(
    monitorRepository,
    meterRegistry,
    eventDispatcher,
    monitorType,
) {

    override fun updateValue(existingValue: AtomicReference<Double>, newValue: Double) {
        existingValue.set(newValue)
    }

    override fun register(monitor: MONITOR, initialValue: Double): MeterDefinition<AtomicReference<Double>> {
        logger.debug("Registering decimal gauge for monitor with ID: ${monitor.id}")
        val value = AtomicReference(initialValue)
        val gauge = Gauge
            .builder(prefixedMeterName(), value) { it.get() }
            .nameTag(name = monitor.name)
        monitor.gaugeTarget()?.let { gauge.targetTag(it) }

        return MeterDefinition(gauge.register(meterRegistry).id, value)
    }
}

/**
 * What the `target` tag of a monitor's gauge says, which is whatever identifies the thing being watched. Push
 * monitors have no target to name, so they contribute none.
 */
private fun MonitorRecord.gaugeTarget(): String? = when (this) {
    is HttpMonitorRecord -> safeDisplayUrl
    is IcmpMonitorRecord -> host
    is TcpMonitorRecord -> "$host:$port"
    is DnsMonitorRecord -> host
    is DockerMonitorRecord -> "$dockerHost/$container"
    else -> null
}

private fun Gauge.Builder<*>.nameTag(name: String): Gauge.Builder<*> = this.tag("name", name)
private fun Gauge.Builder<*>.targetTag(target: String): Gauge.Builder<*> = this.tag("target", target)
