package com.kuvaszuptime.kuvasz.metrics

import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * MetricsExportRegistry is responsible for initializing the available metrics exporters.
 */
@Requires(bean = MeterRegistry::class)
@Singleton
class MetricsExportRegistry(
    private val monitorRepository: MonitorRepository,
    private val metricsExporters: List<MetricsExporter>,
) {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Reads the actually available monitors from the database and initializes the metrics exporters with them
     */
    fun initialize() {
        val monitors = monitorRepository.fetchByEnabled(enabled = true)
        metricsExporters.forEach { exporter ->
            logger.debug("Initializing exporter: ${exporter::class.java.simpleName} for ${monitors.size} monitors")
            exporter.initialize(monitors)
        }
    }
}
