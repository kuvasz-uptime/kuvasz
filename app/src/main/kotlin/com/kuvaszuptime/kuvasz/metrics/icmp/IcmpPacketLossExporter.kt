package com.kuvaszuptime.kuvasz.metrics.icmp

import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.icmp.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.IcmpMetricsLogRepository
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
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.icmp-latest-packet-loss", value = StringUtils.TRUE),
)
class IcmpPacketLossExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val metricsRepository: IcmpMetricsLogRepository,
    monitorRepository: SharedMonitorRepository,
) : GaugeExporter<Int, IcmpMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.ICMP,
) {

    companion object {
        private const val MONITOR_PACKET_LOSS = "icmp.packet.loss.latest.percentage"
    }

    override val meterName = MONITOR_PACKET_LOSS

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToIcmpMonitorUpEvents { event ->
            handleEvent(event.monitor, event.packetLossPercentage)
        }
        eventDispatcher.subscribeToIcmpMonitorDownEvents { event ->
            handleEvent(event.monitor, event.packetLossPercentage)
        }
    }

    private fun handleEvent(monitor: IcmpMonitorRecord, packetLossPercentage: Int) {
        logger.debug("Updating packet loss for monitor with ID: ${monitor.id} to $packetLossPercentage")
        upsertMeter(monitor.numericMonitorId(), packetLossPercentage)
    }

    override fun transform(valueSource: Int): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: IcmpMonitorRecord): Int? =
        metricsRepository.fetchLastByMonitorId(monitor.id)?.packetLossPercentage

    override fun filterCondition(monitor: IcmpMonitorRecord): Boolean = monitor.enabled
}
