package com.kuvaszuptime.kuvasz.services.integrations

import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.models.dto.integration.DiscordNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.EmailNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.IntegrationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.PagerdutyConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.SlackNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.TelegramNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.integration.WebhookNotificationConfigDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.IntegrationDetailsDto
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationMap
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.type
import io.micronaut.context.annotation.Context
import io.pebbletemplates.pebble.PebbleEngine
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory

@Context
class IntegrationRepository(
    private val integrationConfigs: List<IntegrationConfig>,
    private val smtpConfig: SMTPMailerConfig?,
    private val templateEngine: PebbleEngine?,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    val configuredIntegrations: IntegrationMap by lazy {
        val result = mutableMapOf<IntegrationID, IntegrationConfig>()
        integrationConfigs.forEach { integrationConfig ->
            // Validate integration names
            if (integrationConfig.name.isBlank()) {
                throw IntegrationConfigException(
                    "Invalid integration name [${integrationConfig.name}]. " +
                        "Integration name must be a non-blank string."
                )
            }
            // Check for duplicate integration IDs
            if (integrationConfig.id in result) {
                throw IntegrationConfigException(
                    "Duplicate integration configuration found for ${integrationConfig.id}. " +
                        "Please ensure each integration has a unique name."
                )
            }

            // Validate webhook templates
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            if (integrationConfig is WebhookNotificationConfig && templateEngine != null) {
                try {
                    integrationConfig.payloadTemplate
                        ?.takeIf { it.isNotEmpty() }
                        ?.let { templateEngine.getTemplate(it) }
                    integrationConfig.requestHeaders?.values?.forEach { templateEngine.getTemplate(it) }
                } catch (ex: Exception) {
                    throw IntegrationConfigException(
                        "Failed to parse payload/header template for ${integrationConfig.id}: ${ex.message}",
                    )
                }
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
            .let { logger.info("Configured integrations: [$it]") }
        enabledIntegrations.entries
            .joinToString(", ") { it.key.toString() }
            .let { logger.info("Enabled integrations: [$it]") }
        enabledIntegrations.entries
            .asSequence()
            .filter { it.value.global }
            .joinToString(", ") { it.key.toString() }
            .let { logger.info("Globally enabled integrations: [$it]") }
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
    fun getEffectiveIntegrations(rawIntegrations: Set<IntegrationID>): List<IntegrationDetailsDto> =
        configuredIntegrations.filter { (id, config) ->
            (config.global && config.enabled) || rawIntegrations.contains(id)
        }.values.map { IntegrationDetailsDto.fromConfig(it) }

    fun getConfiguredIntegrationDtos(): List<IntegrationConfigDto> = configuredIntegrations.values
        .map { config ->
            when (config) {
                is SlackNotificationConfig -> SlackNotificationConfigDto(config.id, config)
                is DiscordNotificationConfig -> DiscordNotificationConfigDto(config.id, config)
                is PagerdutyConfig -> PagerdutyConfigDto(config.id, config)
                is EmailNotificationConfig -> EmailNotificationConfigDto(config.id, config)
                is TelegramNotificationConfig -> TelegramNotificationConfigDto(config.id, config)
                is WebhookNotificationConfig -> WebhookNotificationConfigDto(config.id, config)
            }
        }
}

class IntegrationConfigException(message: String) : Exception(message)
