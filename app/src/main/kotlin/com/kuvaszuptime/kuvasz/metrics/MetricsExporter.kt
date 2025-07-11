package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.tables.records.MonitorRecord
import com.kuvaszuptime.kuvasz.models.events.MonitorDeleteEvent
import com.kuvaszuptime.kuvasz.models.events.MonitorUpdateEvent
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * A marker interface for all metrics exporters.
 */
interface MetricsExporter {

    companion object {
        private const val PREFIX = "kuvasz"
    }

    /**
     * The name of the meter to be registered in the meter registry.
     */
    val meterName: String

    /**
     * Returns the prefixed meter name, which is a combination of the prefix and the meter name.
     * This is used to avoid naming conflicts in the meter registry with built-in metrics and to have a separate
     * "namespace" in the external monitoring systems.
     */
    fun prefixedMeterName(): String = "$PREFIX.$meterName"

    /**
     * Initializes the exporter with the provided list of monitors.
     * It should:
     *  - register meters for each monitor if the monitor meets the exporter-specific filter condition
     *  - set the initial values for the meters based on the monitors' current state
     *  - listen to relevant events to update the meters dynamically
     *  - listen to monitor lifecycle events to re-create or remove meters as needed
     *
     * This method is called once per exporter during application startup to set up the metrics exporters.
     */
    fun initialize(monitors: List<MonitorRecord>)
}

/**
 * Base class for metrics exporters that provides common functionality for registering and updating meters.
 * This class is generic and can be used for different types of metrics exporters, such as gauges or counters.
 *
 * @param SOURCE_VAL The type of the source value, which is typically a property of a monitor.
 * @param INTERNAL_VAL The type of the internal value representation, which is an intermediary type between the source
 * and the meter value.
 * @param METER_VAL The type of the value that will be used as a reference in the meter registry, e.g. for a gauge, it's
 * an [java.util.concurrent.atomic.AtomicLong].
 */
abstract class BaseMetricsExporter<SOURCE_VAL : Any, INTERNAL_VAL : Any, METER_VAL : Any>(
    private val monitorRepository: MonitorRepository,
    private val meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
) : MetricsExporter {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * A map to hold the meter definitions for each monitor.
     * The key is the monitor ID, and the value is the [MeterDefinition] containing the meter's ID and its value.
     * Keeping tab on the meter's ID allows us to re-create or remove the meter later if needed.
     */
    protected val meterDefinitions: ConcurrentHashMap<Long, MeterDefinition<METER_VAL>> = ConcurrentHashMap()

    /**
     * Exporter specific method to subscribe to the relevant events.
     */
    protected abstract fun subscribeToEvents()

    /**
     * Determines whether the monitor should be included in the meter registration.
     */
    protected abstract fun filterCondition(monitor: MonitorRecord): Boolean

    /**
     * Transforms the value SOURCE into INTERNAL_VAL for the internal representation.
     */
    protected abstract fun transform(valueSource: SOURCE_VAL): INTERNAL_VAL

    /**
     * Returns the initial value for the meter based on the monitors actual state
     */
    protected abstract fun computeInitialValue(monitor: MonitorRecord): SOURCE_VAL?

    /**
     * Registers a meter for the given monitor with the provided initial value and returns the meter definition.
     * Keeping a reference to the meter is the responsibility of the inheriting class.
     */
    protected abstract fun register(monitor: MonitorRecord, initialValue: INTERNAL_VAL): MeterDefinition<METER_VAL>

    /**
     * Updates the value of the existing meter with the new value.
     * This method is responsible for atomically updating the value in the meter registry, keeping the old value's
     * reference intact, which is vital for Micrometer's meter registry to work correctly.
     */
    protected abstract fun updateValue(existingValue: METER_VAL, newValue: INTERNAL_VAL)

    /**
     * Deletes the meter for the given monitor ID if it exists and also de-registers it from the meter registry.
     */
    private fun deleteMeter(monitorId: Long) {
        meterDefinitions.remove(monitorId)?.let { meterDefinition ->
            logger.debug("Removing meter of monitor with ID: $monitorId")
            meterRegistry.remove(meterDefinition.id)
        }
    }

    /**
     * Creates a meter for the given monitor ID with an optional initial value.
     */
    private fun createMeter(monitorId: Long, initialValue: SOURCE_VAL?) {
        monitorRepository.findById(monitorId)?.let { monitor -> createMeter(monitor, initialValue) }
    }

    /**
     * Creates a meter for the given monitor record if it meets the filter condition.
     * Providing an initial value from the outside is optional, and if not provided, it will be computed via
     * [computeInitialValue].
     * Providing an external initial value is useful when the meter is created on the fly and the initial value is known
     * from the underlying event.
     */
    private fun createMeter(monitor: MonitorRecord, initialValue: SOURCE_VAL?) {
        if (filterCondition(monitor)) {
            (initialValue ?: computeInitialValue(monitor))?.let { initVal ->
                register(monitor, transform(initVal)).also { meterDef ->
                    meterDefinitions[monitor.id] = meterDef
                }
            } ?: run {
                logger.debug("Skipping creation for monitor with ID: ${monitor.id} due to null initial value")
            }
        } else {
            logger.debug("Skipping creation for monitor with ID: ${monitor.id} due to filter condition not met")
        }
    }

    /**
     * Updates the meter for the given monitor by applying the transformation to the new source value, or creates a new
     * meter if it does not exist yet.
     */
    protected fun upsertMeter(monitorId: Long, newValue: SOURCE_VAL) {
        meterDefinitions[monitorId]?.value?.let { existingValue ->
            updateValue(existingValue, newValue = transform(newValue))
        } ?: run {
            logger.debug("Meter for monitor with ID: $monitorId not found, creating a new one.")
            createMeter(monitorId, initialValue = newValue)
        }
    }

    override fun initialize(monitors: List<MonitorRecord>) {
        monitors.forEach { monitor ->
            createMeter(monitor, null)
        }

        // Subclass specific subscriptions
        subscribeToEvents()

        // Additional subscription to monitor lifecycle events to handle updates, and deletions of monitors.
        // Creation is not covered purposefully, as the initial registration is either done upon startup, or when
        // the given exporter tries to update the meter for the first time.
        // This means that the meter will be created only when we have a value to register.
        eventDispatcher.subscribeToMonitorLifecycleEvents { event ->
            when (event) {
                is MonitorUpdateEvent -> {
                    deleteMeter(event.monitorId)
                    createMeter(event.monitorId, null)
                }

                is MonitorDeleteEvent -> deleteMeter(event.monitorId)
            }
        }
    }
}
