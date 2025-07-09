package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.MonitorCreateEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Base class for gauge exporters that provides common functionality for registering and updating gauges.
 */
abstract class GaugeExporter<T : Any>(
    private val meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: MonitorRepository,
) : MetricsExporter {

    companion object {
        protected const val NA_GAUGE_VALUE = -1L
    }

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * The name of the gauge to be registered in the meter registry.
     */
    abstract override val meterName: String

    /**
     * A map to hold the gauge definitions for each monitor.
     * The key is the monitor ID, and the value is the GaugeDefinition containing the gauge ID and its value.
     * Keeping tab on the gauge ID allows us to re-create or remove the gauge later if needed.
     */
    private val gaugeDefinitions: ConcurrentHashMap<Long, GaugeDefinition> = ConcurrentHashMap()

    /**
     * Exporter specific method to subscribe to the relevant events.
     */
    protected abstract fun subscribeToEvents()

    /**
     * Transforms the value source into a Long value for the gauge.
     */
    protected abstract fun transform(valueSource: T?): Long

    /**
     * Returns the initial value for the gauge based on the monitor details.
     */
    protected abstract fun initialValue(monitor: MonitorDetailsDto): T?

    /**
     * Determines whether the monitor should be included in the gauge registration.
     * By default, all monitors are included, but this can be overridden in subclasses.
     */
    protected abstract fun filterCondition(monitor: MonitorDetailsDto): Boolean

    private fun MonitorDetailsDto.register() {
        if (filterCondition(this)) {
            logger.debug("Registering gauge for monitor with ID: $id")
            registerGauge(
                monitorId = id,
                monitorName = name,
                monitorUrl = url,
                source = initialValue(this),
            )
        }
    }

    /**
     * Initializes the exporter with the provided list of monitors.
     * It registers gauges for each monitor that meets the filter condition and sets their initial values.
     * Finally, it subscribes to the relevant events to update the gauges dynamically and also to monitor updates to be
     * able to re-create or remove gauges as needed.
     */
    override fun initialize(monitors: List<MonitorDetailsDto>) {
        monitors.forEach { monitor -> monitor.register() }

        // Subclass specific subscriptions
        subscribeToEvents()

        // Additional subscription to monitor lifecycle events to handle creation, updates, and deletions of monitors.
        eventDispatcher.subscribeToMonitorLifecycleEvents { event ->
            when (event) {
                is MonitorCreateEvent -> createGauge(event.monitorId)

                is MonitorUpdateEvent -> {
                    deleteGauge(event.monitorId)
                    createGauge(event.monitorId)
                }

                is MonitorDeleteEvent -> deleteGauge(event.monitorId)
            }
        }
    }

    private fun createGauge(monitorId: Long) {
        monitorRepository.getMonitorWithDetails(monitorId)?.register()
    }

    private fun deleteGauge(monitorId: Long) {
        gaugeDefinitions.remove(monitorId)?.let { gaugeDefinition ->
            logger.debug("Removing gauge of monitor with ID: $monitorId")
            meterRegistry.remove(gaugeDefinition.id)
        }
    }

    /**
     * Registers a gauge for the given monitor with its initial value.
     * The gauge is registered in the meter registry with common tags (monitor name and URL as of now).
     */
    fun registerGauge(monitorId: Long, monitorName: String, monitorUrl: URI, source: T?) {
        val value = AtomicLong(transform(source))
        val gauge = Gauge
            .builder(prefixedMeterName(), value) { it.toDouble() }
            .commonTags(monitorName, monitorUrl)
            .register(meterRegistry)
        gaugeDefinitions[monitorId] = GaugeDefinition(id = gauge.id, value)
    }

    /**
     * Updates the gauge for the given monitor by applying the transformation to the new source value.
     */
    fun updateGauge(monitorId: Long, value: T) {
        gaugeDefinitions[monitorId]?.value?.set(transform(value))
    }

    private fun Gauge.Builder<*>.commonTags(name: String, url: URI): Gauge.Builder<*> = this
        .tag("url", url.toString())
        .tag("name", name)
}
