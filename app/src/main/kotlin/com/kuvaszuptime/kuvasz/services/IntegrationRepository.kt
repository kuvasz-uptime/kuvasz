package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.models.dto.IntegrationDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationMap
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.type
import io.micronaut.context.annotation.Context
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory

@Context
class IntegrationRepository(
    private val integrationConfigs: List<IntegrationConfig>,
    private val smtpConfig: SMTPMailerConfig?,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    // Regex to validate integration names (alphanumeric, hyphens and underscores only)
    private val integrationNameRegex = Regex("^[a-zA-Z0-9_-]+$")

    val configuredIntegrations: IntegrationMap by lazy {
        val result = mutableMapOf<IntegrationID, IntegrationConfig>()
        integrationConfigs.forEach { integrationConfig ->
            // Validate integration names
            if (!integrationConfig.name.matches(integrationNameRegex)) {
                throw IntegrationConfigException(
                    "Invalid integration name [${integrationConfig.name}]. " +
                        "Integration names must be alphanumeric and can contain underscores or hyphens only."
                )
            }
            // Check for duplicate integration IDs
            if (integrationConfig.id in result) {
                throw IntegrationConfigException(
                    "Duplicate integration configuration found for ${integrationConfig.id}. " +
                        "Please ensure each integration has a unique name."
                )
            }
            result[integrationConfig.id] = integrationConfig
        }
        result.toMap()
    }

    val enabledIntegrations: IntegrationMap by lazy {
        val result = mutableMapOf<IntegrationID, IntegrationConfig>()
        configuredIntegrations.forEach { (id, config) ->
            if (config is EmailNotificationConfig) {
                // Only add EmailNotificationConfig if SMTPMailerConfig is available
                if (config.enabled && smtpConfig != null) {
                    result[id] = config
                } else {
                    logger.warn(
                        "Skipping email integration [$id] because it's either disabled or SMTP config is not available."
                    )
                }
            } else if (config.enabled) {
                result[id] = config
            }
        }
        result.toMap()
    }

    val enabledIntegrationsByType: Map<IntegrationType, Set<IntegrationConfig>> by lazy {
        enabledIntegrations.values
            .groupBy { it.type }
            .mapValues { (_, configs) -> configs.toSet() }
            .toMap()
    }

    val globallyEnabledIntegrationsByType: Map<IntegrationType, Set<IntegrationConfig>> by lazy {
        enabledIntegrationsByType.mapValues { (_, configs) ->
            configs.filter { it.global }.toSet()
        }
    }

    @PostConstruct
    fun init() {
        configuredIntegrations.entries
            .joinToString(", ") { it.key.toString() }
            .let {
                logger.info("Configured integrations: [$it]")
            }
        enabledIntegrations.entries
            .joinToString(", ") { it.key.toString() }
            .let {
                logger.info("Enabled integrations: [$it]")
            }
        enabledIntegrations.entries
            .asSequence()
            .filter { it.value.global }
            .joinToString(", ") { it.key.toString() }
            .let {
                logger.info("Globally enabled integrations: [$it]")
            }
    }

    /**
     * Gets a collection of integration IDs (e.g. integrations of a monitor) and returns the configurations of them if
     * they are enabled and of the specified type. It also appends the global integrations to the result.
     * The use case is pretty much when a monitor has multiple integrations, and we want to get the enabled ones to send
     * notifications via them.
     */
    fun getEnabledIntegrations(ids: Array<IntegrationID>, type: IntegrationType): Set<IntegrationConfig> =
        ids
            .mapNotNull { id -> enabledIntegrations[id]?.takeIf { it.type == type } }
            .let { filtered -> globallyEnabledIntegrationsByType[type]?.let { filtered.plus(it) } ?: filtered }
            .toSet()

    /**
     * Returns all the integrations that are effective for the given monitor, including the globally enabled ones
     */
    fun getEffectiveIntegrations(monitor: MonitorDetailsDto): List<IntegrationDetailsDto> =
        configuredIntegrations.filter { (id, config) ->
            (config.global && config.enabled) || monitor.integrations.contains(id)
        }.values.map { IntegrationDetailsDto.fromConfig(it) }
}

class IntegrationConfigException(message: String) : Exception(message)
