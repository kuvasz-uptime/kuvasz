package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * A common, Micrometer based registry for all the custom metrics that are exported by the application.
 */
@Requires(bean = MeterRegistry::class)
@Singleton
class MetricsExportRegistry(
    private val monitorRepository: MonitorRepository,
    private val meterRegistry: MeterRegistry,
) {

    companion object {
        private const val MONITOR_UPTIME_STATUS = "monitor.uptime.status"
    }

    private val uptimeStatusMeters: ConcurrentHashMap<Long, GaugeDefinition> = ConcurrentHashMap()

    /**
     * Reads the actually available monitors from the database and registers their metrics with its initial value
     */
    fun initialize() {
        monitorRepository.getMonitorsWithDetails(enabled = true).forEach { monitor ->
            val value = AtomicLong(monitor.uptimeStatus.toLong())
            val gauge = Gauge.builder(MONITOR_UPTIME_STATUS, value) { it.toDouble() }
                .tag("url", monitor.url.toString())
                .tag("name", monitor.name)
                .register(meterRegistry)
            uptimeStatusMeters[monitor.id] = GaugeDefinition(id = gauge.id, value = value)
        }
    }

    fun updateUptimeStatus(monitorId: Long, uptimeStatus: UptimeStatus) {
        uptimeStatusMeters[monitorId]?.value?.set(uptimeStatus.toLong())
    }

    private fun UptimeStatus?.toLong(): Long =
        when (this) {
            UptimeStatus.UP -> 1L
            UptimeStatus.DOWN -> 0L
            null -> -1L
        }
}
