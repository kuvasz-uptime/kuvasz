package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.GaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.docker.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.DockerUptimeEventRepository
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton

/**
 * How many times the restart policy of the container has restarted it. The count comes with every UP and DOWN event,
 * so unlike the resource gauges it does not depend on the metrics history. A DOWN still pending its failure count
 * threshold dispatches no event, so the gauge only catches up with the restarts it saw at the next event.
 *
 * A check that could not inspect the container leaves the gauge at the last known count instead of removing it: the
 * count is cumulative, so a gap would only break the queries watching it change.
 */
@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.docker-latest-restart-count", value = StringUtils.TRUE),
)
class DockerRestartCountExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val uptimeEventRepository: DockerUptimeEventRepository,
    monitorRepository: SharedMonitorRepository,
) : GaugeExporter<Int, DockerMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DOCKER,
) {

    companion object {
        private const val MONITOR_RESTART_COUNT = "docker.restart.count"
    }

    override val meterName = MONITOR_RESTART_COUNT

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToDockerMonitorUpEvents { event ->
            updateRestartCount(event.monitor, event.restartCount)
        }
        eventDispatcher.subscribeToDockerMonitorDownEvents { event ->
            updateRestartCount(event.monitor, event.restartCount)
        }
    }

    private fun updateRestartCount(monitor: DockerMonitorRecord, restartCount: Int?) {
        restartCount?.let { count ->
            logger.debug("Updating restart count for monitor with ID: ${monitor.id} to $count")
            upsertMeter(monitor.numericMonitorId(), count)
        }
    }

    override fun transform(valueSource: Int): Long = valueSource.toLong()

    override fun computeInitialValue(monitor: DockerMonitorRecord): Int? =
        uptimeEventRepository.fetchLatestRestartCount(monitor.id)

    override fun filterCondition(monitor: DockerMonitorRecord): Boolean = monitor.enabled
}
