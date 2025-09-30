package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.config.StatusPageConfig
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.MetricsExportRegistry
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import io.micronaut.context.annotation.Context
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory

@Context
class AppBootstrapper(
    private val yamlHttpMonitorConfigs: List<HttpMonitorConfig>,
    private val yamlPushMonitorConfigs: List<PushMonitorConfig>,
    private val monitorImporter: MonitorImporter,
    private val appConfig: AppConfig,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val integrationRepository: IntegrationRepository,
    private val httpCheckScheduler: HttpCheckScheduler,
    private val metricsExportRegistry: MetricsExportRegistry?,
    private val yamlStatusPageConfigs: List<StatusPageConfig>,
    private val statusPageImporter: StatusPageImporter,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @PostConstruct
    fun bootstrap() {
        // Process YAML monitor configs if any are present
        processYamlMonitorConfigs()
        // Sanitize the configured integrations on the monitors
        sanitizeIntegrationsOfMonitors()
        // Importing status pages from config if any are present
        processYamlStatusPageConfigs()
        // Conditionally initialize the metrics export if enabled
        metricsExportRegistry?.initialize()
        // Scheduling the initial checks (HTTP uptime & SSL)
        httpCheckScheduler.initialize()

        logger.info("Kuvasz was successfully bootstrapped. Version: ${BuildConfig.APP_VERSION}")
    }

    /**
     * Sanitizes the integrations of all monitors in the database.
     * If an integration is found on a monitor that is not configured, it will be removed from that monitor.
     */
    private fun sanitizeIntegrationsOfMonitors() {
        val configuredIntegrations = integrationRepository.configuredIntegrations.keys

        fun MonitorRecord.sanitizeIntegrations() {
            val originalIntegrations = integrations.toSet()
            val matchedIntegrations = originalIntegrations.intersect(configuredIntegrations)
            if (!matchedIntegrations.containsAll(originalIntegrations)) {
                // There are integrations on the monitor that are not configured, update them
                logger.warn(
                    "Monitor with ID $id has integrations that are not configured: " +
                        "${originalIntegrations - matchedIntegrations}. " +
                        "Updating monitor integrations to only include configured ones."
                )
                when (this) {
                    is HttpMonitorRecord -> httpMonitorRepository.updateIntegrations(
                        id,
                        matchedIntegrations.toTypedArray(),
                    )

                    is PushMonitorRecord -> pushMonitorRepository.updateIntegrations(
                        id,
                        matchedIntegrations.toTypedArray(),
                    )
                }
            }
        }

        // Only sanitize integrations if HTTP monitors were not configured via YAML
        if (!appConfig.isHttpMonitorExternalWriteDisabled()) {
            httpMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations() }
        }

        // Only sanitize integrations if push monitors were not configured via YAML
        if (!appConfig.isPushMonitorExternalWriteDisabled()) {
            pushMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations() }
        }
    }

    /**
     * Processes the YAML monitor configs. If any YAML config is found, it disables external modifications of the
     * respective monitors
     */
    private fun processYamlMonitorConfigs() {
        if (yamlHttpMonitorConfigs.isNotEmpty()) {
            appConfig.disableHttpMonitorExternalWrite()
            logger.info(
                "Disabled external modifications of HTTP monitors, because a YAML monitor config was found. " +
                    "Loading HTTP monitors from YAML config..."
            )
            monitorImporter.importHttpMonitorConfigs(yamlHttpMonitorConfigs)
        } else {
            logger.info(
                "No YAML HTTP monitor config was found. " +
                    "External modifications of HTTP monitors are enabled. Loading monitors from DB..."
            )
        }

        if (yamlPushMonitorConfigs.isNotEmpty()) {
            // Ensuring that all client secrets are unique
            require(yamlPushMonitorConfigs.groupBy { it.clientSecret }.all { it.value.size == 1 }) {
                "YAML push monitor configs must have unique client secrets!"
            }

            appConfig.disablePushMonitorExternalWrite()
            logger.info(
                "Disabled external modifications of push monitors, because a YAML monitor config was found. " +
                    "Loading push monitors from YAML config..."
            )
            monitorImporter.importPushMonitorConfigs(yamlPushMonitorConfigs)
        } else {
            logger.info(
                "No YAML push monitor config was found. " +
                    "External modifications of push monitors are enabled. Loading monitors from DB..."
            )
        }
    }

    /**
     * Processes the YAML status page configs. If any YAML config is found, it disables external modifications of
     * status pages
     */
    private fun processYamlStatusPageConfigs() {
        if (yamlStatusPageConfigs.isNotEmpty()) {
            appConfig.disableStatusPageExternalWrite()
            logger.info(
                "Disabled external modifications of status pages, because a YAML status page config was found. " +
                    "Loading status pages from YAML config..."
            )
            statusPageImporter.importStatusPageConfigs(yamlStatusPageConfigs)
        } else {
            logger.info(
                "No YAML status page config was found. " +
                    "External modifications of status pages are enabled. Loading status pages from DB..."
            )
        }
    }
}
