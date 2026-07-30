package com.kuvaszuptime.kuvasz.metrics.dns

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.metrics.numericMonitorId
import com.kuvaszuptime.kuvasz.metrics.toLong
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
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
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.dns-uptime-status", value = StringUtils.TRUE),
)
class DnsUptimeStatusExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    monitorRepository: SharedMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
) : GaugeExporter<UptimeStatus, DnsMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DNS,
) {

    companion object {
        private const val MONITOR_UPTIME_STATUS = "dns.uptime.status"
    }

    override val meterName = MONITOR_UPTIME_STATUS

    override fun subscribeToEvents() {
        logger.debug("Subscribing to DNS uptime monitor events")
        eventDispatcher.subscribeToDnsMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToDnsMonitorDownEvents { event ->
            event.handle()
        }
    }

    private fun UptimeMonitorEvent.handle() {
        runWhenStateChanges {
            logger.debug("Updating uptime status for monitor with ID: ${monitor.id} to $uptimeStatus")
            upsertMeter(monitor.numericMonitorId(), uptimeStatus)
        }
    }

    override fun transform(valueSource: UptimeStatus): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: DnsMonitorRecord): UptimeStatus? =
        dnsMonitorRepository.getMonitorWithDetails(monitor.id)?.uptimeStatus

    override fun filterCondition(monitor: DnsMonitorRecord): Boolean = monitor.enabled
}
