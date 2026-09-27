package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.metrics.numericMonitorId
import com.kuvaszuptime.kuvasz.metrics.toLong
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.events.UptimeMonitorEvent
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
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
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.docker-uptime-status", value = StringUtils.TRUE),
)
class DockerUptimeStatusExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    monitorRepository: SharedMonitorRepository,
    private val dockerMonitorRepository: DockerMonitorRepository,
) : GaugeExporter<UptimeStatus, DockerMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DOCKER,
) {

    companion object {
        private const val MONITOR_UPTIME_STATUS = "docker.uptime.status"
    }

    override val meterName = MONITOR_UPTIME_STATUS

    override fun subscribeToEvents() {
        logger.debug("Subscribing to Docker uptime monitor events")
        eventDispatcher.subscribeToDockerMonitorUpEvents { event ->
            event.handle()
        }
        eventDispatcher.subscribeToDockerMonitorDownEvents { event ->
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

    override fun computeInitialValue(monitor: DockerMonitorRecord): UptimeStatus? =
        dockerMonitorRepository.getMonitorWithDetails(monitor.id)?.uptimeStatus

    override fun filterCondition(monitor: DockerMonitorRecord): Boolean = monitor.enabled
}
