package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
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
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.uptime-status", value = StringUtils.TRUE),
)
class UptimeStatusExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    monitorRepository: MonitorRepository,
) : GaugeExporter<UptimeStatus>(meterRegistry, eventDispatcher, monitorRepository) {

    companion object {
        private const val MONITOR_UPTIME_STATUS = "monitor.uptime.status"
    }

    override val meterName = MONITOR_UPTIME_STATUS

    override fun subscribeToEvents() {
        logger.debug("Subscribing to uptime monitor events")
        eventDispatcher.subscribeToMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToMonitorDownEvents { event ->
            event.handle()
        }
    }

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges {
            logger.debug("Updating uptime status for monitor with ID: ${monitor.id} to $uptimeStatus")
            updateGauge(monitor.id, uptimeStatus)
        }
    }

    override fun transform(valueSource: UptimeStatus?): Long =
        when (valueSource) {
            UptimeStatus.UP -> 1L
            UptimeStatus.DOWN -> 0L
            null -> NA_GAUGE_VALUE
        }

    override fun initialValue(monitor: MonitorDetailsDto): UptimeStatus? = monitor.uptimeStatus

    override fun filterCondition(monitor: MonitorDetailsDto): Boolean = monitor.enabled
}
