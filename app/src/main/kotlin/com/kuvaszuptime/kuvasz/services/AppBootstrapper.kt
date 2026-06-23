package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.ApiKeyConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.IcmpMonitorConfig
import com.kuvaszuptime.kuvasz.config.MaintenanceWindowConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.config.StatusPageConfig
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.MetricsExportRegistry
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.security.api.HeaderApiKeyReader.Companion.API_KEY_MIN_LENGTH
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowImporter
import com.kuvaszuptime.kuvasz.services.monitor.MonitorImporter
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageImporter
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.annotation.Nullable
import jakarta.annotation.PostConstruct
import jakarta.validation.ValidationException
import org.slf4j.LoggerFactory

@Context
class AppBootstrapper(
    private val yamlHttpMonitorConfigs: List<HttpMonitorConfig>,
    private val yamlPushMonitorConfigs: List<PushMonitorConfig>,
    private val yamlIcmpMonitorConfigs: List<IcmpMonitorConfig>,
    private val monitorImporter: MonitorImporter,
    private val appConfig: AppConfig,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val integrationRepository: IntegrationRepository,
    private val httpCheckScheduler: HttpCheckScheduler,
    private val icmpCheckScheduler: IcmpCheckScheduler,
    private val metricsExportRegistry: MetricsExportRegistry?,
    private val yamlStatusPageConfigs: List<StatusPageConfig>,
    private val statusPageImporter: StatusPageImporter,
    private val yamlMaintenanceWindowConfigs: List<MaintenanceWindowConfig>,
    private val maintenanceWindowImporter: MaintenanceWindowImporter,
    private val apiKeyConfig: ApiKeyConfig?,
) {

    @Suppress("ProtectedMemberInFinalClass")
    @Nullable
    @field:Property(name = HttpMonitorConfig.CONFIG_PREFIX)
    protected var httpMonitorYAMLConfigChecker: List<Any>? = null

    @Suppress("ProtectedMemberInFinalClass")
    @Nullable
    @field:Property(name = PushMonitorConfig.CONFIG_PREFIX)
    protected var pushMonitorYAMLConfigChecker: List<Any>? = null

    @Suppress("ProtectedMemberInFinalClass")
    @Nullable
    @field:Property(name = IcmpMonitorConfig.CONFIG_PREFIX)
    protected var icmpMonitorYAMLConfigChecker: List<Any>? = null

    @Suppress("ProtectedMemberInFinalClass")
    @Nullable
    @field:Property(name = StatusPageConfig.CONFIG_PREFIX)
    protected var statusPagesYAMLConfigChecker: List<Any>? = null

    @Suppress("ProtectedMemberInFinalClass")
    @Nullable
    @field:Property(name = MaintenanceWindowConfig.CONFIG_PREFIX)
    protected var maintenanceWindowsYAMLConfigChecker: List<Any>? = null

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @PostConstruct
    fun bootstrap() {
        // Validate the API keys in case they are set to a non-null value
        validateApiKeys()
        // Process YAML monitor configs if any are present
        processYamlMonitorConfigs()
        // Sanitize the configured integrations on the monitors
        sanitizeIntegrationsOfMonitors()
        // Importing status pages from config if any are present
        processYamlStatusPageConfigs()
        // Importing maintenance windows from config if any are present
        processYamlMaintenanceWindowConfigs()
        // Conditionally initialize the metrics export if enabled
        metricsExportRegistry?.initialize()
        // Scheduling the initial checks (HTTP uptime & SSL)
        httpCheckScheduler.initialize()
        // Scheduling the initial ICMP uptime checks
        icmpCheckScheduler.initialize()

        logger.info("Kuvasz was successfully bootstrapped. Version: ${BuildConfig.APP_VERSION}")
    }

    private fun validateApiKeys() {
        val config = apiKeyConfig ?: return
        validateApiKeyLength(config.apiKey?.takeIf { config.isApiKeyDisabled().not() }, "Admin API key")
        validateApiKeyLength(config.mcpApiKey?.takeIf { config.isMcpApiKeyDisabled().not() }, "MCP API key")
    }

    private fun validateApiKeyLength(apiKey: String?, name: String) {
        if (apiKey != null && apiKey.length < API_KEY_MIN_LENGTH) {
            throw ValidationException("$name must be at least $API_KEY_MIN_LENGTH characters")
        }
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

                    is IcmpMonitorRecord -> icmpMonitorRepository.updateIntegrations(
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

        // Only sanitize integrations if ICMP monitors were not configured via YAML
        if (!appConfig.isIcmpMonitorExternalWriteDisabled()) {
            icmpMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations() }
        }
    }

    /**
     * Processes the YAML monitor configs. If any YAML config is found, it disables external modifications of the
     * respective monitors
     */
    private fun processYamlMonitorConfigs() {
        // The httpMonitorYAMLConfigChecker is a workaround to check if the http-monitors config is present in the
        // YAML configuration file or not. If it's explicitly set to an empty list, it means that the user wants to
        // have zero HTTP monitors, so we should disable external writes and delete all monitors from the
        // DB eventually.
        val isYamlHttpConfigEffective = yamlHttpMonitorConfigs.isNotEmpty() || httpMonitorYAMLConfigChecker != null
        if (isYamlHttpConfigEffective) {
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

        // The pushMonitorYAMLConfigChecker is a workaround to check if the push-monitors config is present in the
        // YAML configuration file or not. If it's explicitly set to an empty list, it means that the user wants to
        // have zero push monitors, so we should disable external writes and delete all monitors from the
        // DB eventually.
        val isYamlPushConfigEffective = yamlPushMonitorConfigs.isNotEmpty() || pushMonitorYAMLConfigChecker != null
        if (isYamlPushConfigEffective) {
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

        // The icmpMonitorYAMLConfigChecker is a workaround to check if the icmp-monitors config is present in the
        // YAML configuration file or not. If it's explicitly set to an empty list, it means that the user wants to
        // have zero ICMP monitors, so we should disable external writes and delete all monitors from the
        // DB eventually.
        val isYamlIcmpConfigEffective = yamlIcmpMonitorConfigs.isNotEmpty() || icmpMonitorYAMLConfigChecker != null
        if (isYamlIcmpConfigEffective) {
            appConfig.disableIcmpMonitorExternalWrite()
            logger.info(
                "Disabled external modifications of ICMP monitors, because a YAML monitor config was found. " +
                    "Loading ICMP monitors from YAML config..."
            )
            monitorImporter.importIcmpMonitorConfigs(yamlIcmpMonitorConfigs)
        } else {
            logger.info(
                "No YAML ICMP monitor config was found. " +
                    "External modifications of ICMP monitors are enabled. Loading monitors from DB..."
            )
        }
    }

    /**
     * Processes the YAML status page configs. If any YAML config is found, it disables external modifications of
     * status pages
     */
    private fun processYamlStatusPageConfigs() {
        // The statusPagesYAMLConfigChecker is a workaround to check if the status-pages config is present in the
        // YAML configuration file or not. If it's explicitly set to an empty list, it means that the user wants to
        // have zero status pages, so we should disable external writes and delete all status pages from the
        // DB eventually.
        val isYamlStatusPageConfigEffective = yamlStatusPageConfigs.isNotEmpty() || statusPagesYAMLConfigChecker != null
        if (isYamlStatusPageConfigEffective) {
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

    /**
     * Processes the YAML maintenance window configs. If any YAML config is found, it disables external modifications
     * of maintenance windows
     */
    private fun processYamlMaintenanceWindowConfigs() {
        // The maintenanceWindowsYAMLConfigChecker is a workaround to check if the maintenance-windows config is present
        // in the YAML configuration file or not. If it's explicitly set to an empty list, it means that the user wants
        // to have zero maintenance windows, so we should disable external writes and delete all windows from the
        // DB eventually.
        val isYamlConfigEffective =
            yamlMaintenanceWindowConfigs.isNotEmpty() || maintenanceWindowsYAMLConfigChecker != null
        if (isYamlConfigEffective) {
            appConfig.disableMaintenanceWindowExternalWrite()
            logger.info(
                "Disabled external modifications of maintenance windows, because a YAML config was found. " +
                    "Loading maintenance windows from YAML config..."
            )
            maintenanceWindowImporter.importMaintenanceWindowConfigs(yamlMaintenanceWindowConfigs)
        } else {
            logger.info(
                "No YAML maintenance window config was found. " +
                    "External modifications of maintenance windows are enabled. Loading maintenance windows from DB..."
            )
        }
    }
}
