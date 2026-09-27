package com.kuvaszuptime.kuvasz.metrics.docker

import com.kuvaszuptime.kuvasz.jooq.tables.records.DockerMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.DecimalGaugeExporter
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.docker.numericMonitorId
import com.kuvaszuptime.kuvasz.repositories.DockerMetricsLogRepository
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import com.kuvaszuptime.kuvasz.services.EventDispatcher
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requirements
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import jakarta.inject.Singleton
import java.math.BigDecimal

/**
 * The container's CPU usage, which only a monitor with a metrics history produces: the sampling costs an extra
 * Docker API call, so a monitor without one reports nothing here and gets no meter at all.
 */
@Singleton
@Requirements(
    Requires(bean = MeterRegistry::class),
    Requires(property = "${MetricsExportConfig.CONFIG_PREFIX}.docker-latest-cpu-usage", value = StringUtils.TRUE),
)
class DockerCpuUsageExporter(
    meterRegistry: MeterRegistry,
    private val eventDispatcher: EventDispatcher,
    private val metricsRepository: DockerMetricsLogRepository,
    monitorRepository: SharedMonitorRepository,
) : DecimalGaugeExporter<BigDecimal, DockerMonitorRecord>(
    meterRegistry,
    eventDispatcher,
    monitorRepository,
    MonitorType.DOCKER,
) {

    companion object {
        private const val MONITOR_CPU_USAGE = "docker.cpu.usage.latest.percent"
    }

    override val meterName = MONITOR_CPU_USAGE

    override fun subscribeToEvents() {
        eventDispatcher.subscribeToDockerMonitorUpEvents { event ->
            updateCpuUsage(event.monitor, event.cpuUsagePercent)
        }
        // A down event never carries a sample, not even for a container that is still running but unhealthy
        eventDispatcher.subscribeToDockerMonitorDownEvents { event ->
            updateCpuUsage(event.monitor, cpuUsage = null)
        }
    }

    /**
     * Without a fresh sample the container's usage is unknown, so the meter is removed rather than left reporting
     * the last reading of a container that may have long stopped. The next sample registers it again.
     */
    private fun updateCpuUsage(monitor: DockerMonitorRecord, cpuUsage: BigDecimal?) {
        if (cpuUsage == null) {
            deleteMeter(monitor.numericMonitorId())
        } else {
            logger.debug("Updating CPU usage for monitor with ID: ${monitor.id} to $cpuUsage")
            upsertMeter(monitor.numericMonitorId(), cpuUsage)
        }
    }

    override fun transform(valueSource: BigDecimal): Double = valueSource.toDouble()

    override fun computeInitialValue(monitor: DockerMonitorRecord): BigDecimal? =
        metricsRepository.fetchLastByMonitorId(monitor.id)?.cpuUsagePercent

    override fun filterCondition(monitor: DockerMonitorRecord): Boolean =
        monitor.enabled && monitor.metricsHistoryEnabled
}
