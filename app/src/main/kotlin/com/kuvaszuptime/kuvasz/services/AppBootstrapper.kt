package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.MonitorConfig
import com.kuvaszuptime.kuvasz.metrics.MetricsExportRegistry
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.micronaut.context.annotation.Context
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory

@Context
class AppBootstrapper(
    private val yamlMonitorConfigs: List<MonitorConfig>,
    private val monitorImporter: MonitorImporter,
    private val appConfig: AppConfig,
    private val monitorRepository: MonitorRepository,
    private val integrationRepository: IntegrationRepository,
    private val checkScheduler: CheckScheduler,
    private val metricsExportRegistry: MetricsExportRegistry?,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @PostConstruct
    fun bootstrap() {
        // Process YAML monitor configs if any are present
        processYamlMonitorConfigs()
        // Sanitize the configured integrations on the monitors
        sanitizeIntegrationsOfMonitors()
        // Conditionally initialize the metrics export if enabled
        metricsExportRegistry?.initialize()
        // Scheduling the initial checks (uptime & SSL)
        checkScheduler.initialize()

        logger.info("Kuvasz was successfully bootstrapped. Version: ${BuildConfig.APP_VERSION}")
    }

    /**
     * Sanitizes the integrations of all monitors in the database.
     * If an integration is found on a monitor that is not configured, it will be removed from that monitor.
     */
    private fun sanitizeIntegrationsOfMonitors() {
        // Only sanitize integrations if monitors were not configured via YAML
        if (!appConfig.isExternalWriteDisabled()) {
            val configuredIntegrations = integrationRepository.configuredIntegrations.keys

            monitorRepository.fetchAll().forEach { monitor ->
                val originalIntegrations = monitor.integrations.toSet()
                val matchedIntegrations = originalIntegrations.intersect(configuredIntegrations)
                if (!matchedIntegrations.containsAll(originalIntegrations)) {
                    // There are integrations on the monitor that are not configured, update them
                    logger.warn(
                        "Monitor with ID ${monitor.id} has integrations that are not configured: " +
                            "${originalIntegrations - matchedIntegrations}. " +
                            "Updating monitor integrations to only include configured ones."
                    )
                    monitorRepository.updateIntegrations(monitor.id, matchedIntegrations.toTypedArray())
                }
            }
        }
    }

    /**
     * Processes the YAML monitor configs. If any YAML config is found, it disables external modifications of monitors
     */
    private fun processYamlMonitorConfigs() {
        if (yamlMonitorConfigs.isNotEmpty()) {
            appConfig.disableExternalWrite()
            logger.info(
                "Disabled external modifications of monitors, because a YAML monitor config was found. " +
                    "Loading monitors from YAML config..."
            )
            monitorImporter.importMonitorConfigs(yamlMonitorConfigs)
        } else {
            logger.info(
                "No YAML monitor config was found. " +
                    "External modifications of monitors are enabled. Loading monitors from DB..."
            )
        }
    }
}
