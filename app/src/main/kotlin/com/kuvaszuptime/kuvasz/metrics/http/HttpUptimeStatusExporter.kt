package com.kuvaszuptime.kuvasz.metrics.http

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.events.HttpUptimeMonitorEvent
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
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.http-uptime-status", value = StringUtils.TRUE),
)
class HttpUptimeStatusExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val monitorRepository: HttpMonitorRepository,
) : HttpGaugeExporter<UptimeStatus>(meterRegistry, eventDispatcher, monitorRepository) {

    companion object {
        private const val MONITOR_UPTIME_STATUS = "http.uptime.status"
    }

    override val meterName = MONITOR_UPTIME_STATUS

    override fun subscribeToEvents() {
        logger.debug("Subscribing to uptime monitor events")
        eventDispatcher.subscribeToHttpMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToHttpMonitorDownEvents { event ->
            event.handle()
        }
    }

    private fun HttpUptimeMonitorEvent.handle() {
        runWhenStateChanges {
            logger.debug("Updating uptime status for monitor with ID: ${monitor.id} to $uptimeStatus")
            upsertMeter(monitor.id, uptimeStatus)
        }
    }

    override fun transform(valueSource: UptimeStatus): Long =
        when (valueSource) {
            UptimeStatus.UP -> 1L
            UptimeStatus.DOWN -> 0L
        }

    override fun computeInitialValue(monitor: HttpMonitorRecord): UptimeStatus? =
        monitorRepository.getMonitorWithDetails(monitor.id)?.uptimeStatus

    override fun filterCondition(monitor: HttpMonitorRecord): Boolean = monitor.enabled
}
