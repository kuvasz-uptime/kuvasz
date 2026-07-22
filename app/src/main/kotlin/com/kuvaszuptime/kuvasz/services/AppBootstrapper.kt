package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.config.ApiKeyConfig
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.IcmpMonitorConfig
import com.kuvaszuptime.kuvasz.config.MaintenanceWindowConfig
import com.kuvaszuptime.kuvasz.config.MonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.config.StatusPageConfig
import com.kuvaszuptime.kuvasz.config.TcpMonitorConfig
import com.kuvaszuptime.kuvasz.jooq.MonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.DnsMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.HttpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.IcmpMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.PushMonitorRecord
import com.kuvaszuptime.kuvasz.jooq.tables.records.TcpMonitorRecord
import com.kuvaszuptime.kuvasz.metrics.MetricsExportRegistry
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorTypeImportResult
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.MaintenanceWindowRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.security.api.HeaderApiKeyReader.Companion.API_KEY_MIN_LENGTH
import com.kuvaszuptime.kuvasz.services.check.dns.DnsCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpCheckScheduler
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpCheckScheduler
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowImporter
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowScheduler
import com.kuvaszuptime.kuvasz.services.monitor.MonitorImporter
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageImporter
import com.kuvaszuptime.kuvasz.util.loggerFor
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Property
import jakarta.annotation.Nullable
import jakarta.annotation.PostConstruct
import jakarta.validation.ValidationException

@Context
class AppBootstrapper(
    private val yamlHttpMonitorConfigs: List<HttpMonitorConfig>,
    private val yamlPushMonitorConfigs: List<PushMonitorConfig>,
    private val yamlIcmpMonitorConfigs: List<IcmpMonitorConfig>,
    private val yamlTcpMonitorConfigs: List<TcpMonitorConfig>,
    private val monitorImporter: MonitorImporter,
    private val appConfig: AppConfig,
    private val httpMonitorRepository: HttpMonitorRepository,
    private val pushMonitorRepository: PushMonitorRepository,
    private val icmpMonitorRepository: IcmpMonitorRepository,
    private val tcpMonitorRepository: TcpMonitorRepository,
    private val dnsMonitorRepository: DnsMonitorRepository,
    private val integrationRepository: IntegrationRepository,
    private val httpCheckScheduler: HttpCheckScheduler,
    private val icmpCheckScheduler: IcmpCheckScheduler,
    private val tcpCheckScheduler: TcpCheckScheduler,
    private val dnsCheckScheduler: DnsCheckScheduler,
    private val metricsExportRegistry: MetricsExportRegistry?,
    private val yamlStatusPageConfigs: List<StatusPageConfig>,
    private val statusPageImporter: StatusPageImporter,
    private val yamlMaintenanceWindowConfigs: List<MaintenanceWindowConfig>,
    private val maintenanceWindowImporter: MaintenanceWindowImporter,
    private val maintenanceWindowRepository: MaintenanceWindowRepository,
    private val maintenanceWindowScheduler: MaintenanceWindowScheduler,
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
    @field:Property(name = TcpMonitorConfig.CONFIG_PREFIX)
    protected var tcpMonitorYAMLConfigChecker: List<Any>? = null

    @Suppress("ProtectedMemberInFinalClass")
    @Nullable
    @field:Property(name = StatusPageConfig.CONFIG_PREFIX)
    protected var statusPagesYAMLConfigChecker: List<Any>? = null

    @Suppress("ProtectedMemberInFinalClass")
    @Nullable
    @field:Property(name = MaintenanceWindowConfig.CONFIG_PREFIX)
    protected var maintenanceWindowsYAMLConfigChecker: List<Any>? = null

    companion object {
        private val logger = loggerFor<AppBootstrapper>()
    }

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
        // Sanitize the configured integrations on the maintenance windows
        sanitizeIntegrationsOfMaintenanceWindows()
        // Conditionally initialize the metrics export if enabled
        metricsExportRegistry?.initialize()
        // Scheduling the initial checks (HTTP uptime & SSL)
        httpCheckScheduler.initialize()
        // Scheduling the initial ICMP uptime checks
        icmpCheckScheduler.initialize()
        // Scheduling the initial TCP uptime checks
        tcpCheckScheduler.initialize()
        // Scheduling the initial DNS uptime checks
        dnsCheckScheduler.initialize()
        // Scheduling the start/end notifications of the enabled maintenance windows
        maintenanceWindowScheduler.initialize()

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

        // Only sanitize integrations if * monitors were not configured via YAML
        if (!appConfig.isHttpMonitorExternalWriteDisabled()) {
            httpMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations(configuredIntegrations) }
        }
        if (!appConfig.isPushMonitorExternalWriteDisabled()) {
            pushMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations(configuredIntegrations) }
        }
        if (!appConfig.isIcmpMonitorExternalWriteDisabled()) {
            icmpMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations(configuredIntegrations) }
        }
        if (!appConfig.isTcpMonitorExternalWriteDisabled()) {
            tcpMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations(configuredIntegrations) }
        }
        // TODO(dns): guard with appConfig.isDnsMonitorExternalWriteDisabled() once DnsMonitorConfig lands (API stage)
        dnsMonitorRepository.fetchAll().forEach { it.sanitizeIntegrations(configuredIntegrations) }
    }

    private fun MonitorRecord.sanitizeIntegrations(configuredIntegrations: Set<IntegrationID>) {
        val originalIntegrations = integrations.toSet()
        val matchedIntegrations = originalIntegrations.intersect(configuredIntegrations)
        if (matchedIntegrations.containsAll(originalIntegrations)) return

        // There are integrations on the monitor that are not configured, update them
        logger.warn(
            "Monitor with ID $id has integrations that are not configured: " +
                "${originalIntegrations - matchedIntegrations}. " +
                "Updating monitor integrations to only include configured ones."
        )
        val newIntegrations = matchedIntegrations.toTypedArray()
        when (this) {
            is HttpMonitorRecord -> httpMonitorRepository.updateIntegrations(id, newIntegrations)
            is PushMonitorRecord -> pushMonitorRepository.updateIntegrations(id, newIntegrations)
            is IcmpMonitorRecord -> icmpMonitorRepository.updateIntegrations(id, newIntegrations)
            is TcpMonitorRecord -> tcpMonitorRepository.updateIntegrations(id, newIntegrations)
            is DnsMonitorRecord -> dnsMonitorRepository.updateIntegrations(id, newIntegrations)
        }
    }

    /**
     * Sanitizes the integrations of all maintenance windows in the database.
     * If an integration is found on a window that is not configured, it will be removed from that window.
     */
    private fun sanitizeIntegrationsOfMaintenanceWindows() {
        if (appConfig.isMaintenanceWindowExternalWriteDisabled()) return

        val configuredIntegrations = integrationRepository.configuredIntegrations.keys

        maintenanceWindowRepository.fetchAll().forEach { window ->
            val originalIntegrations = window.integrations.toSet()
            val matchedIntegrations = originalIntegrations.intersect(configuredIntegrations)
            if (!matchedIntegrations.containsAll(originalIntegrations)) {
                // There are integrations on the window that are not configured, update them
                logger.warn(
                    "Maintenance window with ID ${window.id} has integrations that are not configured: " +
                        "${originalIntegrations - matchedIntegrations}. " +
                        "Updating window integrations to only include configured ones."
                )
                maintenanceWindowRepository.updateIntegrations(window.id, matchedIntegrations.toTypedArray())
            }
        }
    }

    /**
     * Processes the YAML monitor configs. If any YAML config is found, it disables external modifications of the
     * respective monitors
     */
    private fun processYamlMonitorConfigs() {

        processYamlMonitorConfigs(
            monitorType = MonitorType.HTTP_SSL,
            yamlMonitorConfigs = yamlHttpMonitorConfigs,
            yamlConfigChecker = httpMonitorYAMLConfigChecker,
            callToDisableExternalWrite = appConfig::disableHttpMonitorExternalWrite,
            callToImportConfigs = monitorImporter::importHttpMonitorConfigs,
        )

        // Ensuring that all client secrets are unique before importing the push monitors
        require(yamlPushMonitorConfigs.groupBy { it.clientSecret }.all { it.value.size == 1 }) {
            "YAML push monitor configs must have unique client secrets!"
        }
        processYamlMonitorConfigs(
            monitorType = MonitorType.PUSH,
            yamlMonitorConfigs = yamlPushMonitorConfigs,
            yamlConfigChecker = pushMonitorYAMLConfigChecker,
            callToDisableExternalWrite = appConfig::disablePushMonitorExternalWrite,
            callToImportConfigs = monitorImporter::importPushMonitorConfigs,
        )

        processYamlMonitorConfigs(
            monitorType = MonitorType.ICMP,
            yamlMonitorConfigs = yamlIcmpMonitorConfigs,
            yamlConfigChecker = icmpMonitorYAMLConfigChecker,
            callToDisableExternalWrite = appConfig::disableIcmpMonitorExternalWrite,
            callToImportConfigs = monitorImporter::importIcmpMonitorConfigs,
        )

        processYamlMonitorConfigs(
            monitorType = MonitorType.TCP,
            yamlMonitorConfigs = yamlTcpMonitorConfigs,
            yamlConfigChecker = tcpMonitorYAMLConfigChecker,
            callToDisableExternalWrite = appConfig::disableTcpMonitorExternalWrite,
            callToImportConfigs = monitorImporter::importTcpMonitorConfigs,
        )
    }

    private fun <C : MonitorConfig> processYamlMonitorConfigs(
        monitorType: MonitorType,
        yamlMonitorConfigs: List<C>,
        yamlConfigChecker: List<Any>?,
        callToDisableExternalWrite: () -> Unit,
        callToImportConfigs: (yamlConfigs: List<C>, dryRun: Boolean) -> MonitorTypeImportResult,
    ) {
        val typeName = monitorType.name
        val isYamlConfigEffective = yamlMonitorConfigs.isNotEmpty() || yamlConfigChecker != null
        if (isYamlConfigEffective) {
            callToDisableExternalWrite()
            logger.info(
                "Disabled external modifications of $typeName monitors, because a YAML monitor config was found. " +
                    "Loading $typeName monitors from YAML config..."
            )
            callToImportConfigs(yamlMonitorConfigs, false)
        } else {
            logger.info(
                "No YAML $typeName monitor config was found. " +
                    "External modifications of $typeName monitors are enabled. Loading monitors from DB..."
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
            statusPageImporter.importStatusPageConfigs(yamlStatusPageConfigs, dryRun = false)
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
            maintenanceWindowImporter.importMaintenanceWindowConfigs(yamlMaintenanceWindowConfigs, dryRun = false)
        } else {
            logger.info(
                "No YAML maintenance window config was found. " +
                    "External modifications of maintenance windows are enabled. Loading maintenance windows from DB..."
            )
        }
    }
}
