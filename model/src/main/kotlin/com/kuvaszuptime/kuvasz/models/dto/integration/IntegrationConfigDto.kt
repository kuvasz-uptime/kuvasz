package com.kuvaszuptime.kuvasz.models.dto.integration

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.WebhookNotificationConfig
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
    oneOf = [
        SlackNotificationConfigDto::class,
        DiscordNotificationConfigDto::class,
        PagerdutyConfigDto::class,
        EmailNotificationConfigDto::class,
        TelegramNotificationConfigDto::class,
        WebhookNotificationConfigDto::class,
    ]
)
// JSON subtypes are needed only for the tests
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SlackNotificationConfigDto::class, name = "SLACK"),
    JsonSubTypes.Type(value = DiscordNotificationConfigDto::class, name = "DISCORD"),
    JsonSubTypes.Type(value = PagerdutyConfigDto::class, name = "PAGERDUTY"),
    JsonSubTypes.Type(value = EmailNotificationConfigDto::class, name = "EMAIL"),
    JsonSubTypes.Type(value = TelegramNotificationConfigDto::class, name = "TELEGRAM"),
    JsonSubTypes.Type(value = WebhookNotificationConfigDto::class, name = "WEBHOOK"),
)
sealed interface IntegrationConfigDto {
    val id: IntegrationID
    val type: IntegrationType
    val name: String
    val enabled: Boolean
    val global: Boolean
    val excludedEvents: List<IntegrationEventType>
}

@Introspected
data class SlackNotificationConfigDto(
    override val id: IntegrationID,
    @param:Schema(description = IntegrationDocs.TYPE, required = true)
    override val type: IntegrationType,
    @param:Schema(description = IntegrationDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = IntegrationDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = IntegrationDocs.GLOBAL, required = true)
    override val global: Boolean,
    @param:Schema(description = IntegrationDocs.EXCLUDED_EVENTS, required = true)
    override val excludedEvents: List<IntegrationEventType>,
) : IntegrationConfigDto {
    constructor(integrationID: IntegrationID, config: SlackNotificationConfig) : this(
        id = integrationID,
        type = integrationID.type,
        name = config.name,
        enabled = config.enabled,
        global = config.global,
        excludedEvents = config.excludedEvents.orEmpty(),
    )
}

@Introspected
data class DiscordNotificationConfigDto(
    override val id: IntegrationID,
    @param:Schema(description = IntegrationDocs.TYPE, required = true)
    override val type: IntegrationType,
    @param:Schema(description = IntegrationDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = IntegrationDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = IntegrationDocs.GLOBAL, required = true)
    override val global: Boolean,
    @param:Schema(description = IntegrationDocs.EXCLUDED_EVENTS, required = true)
    override val excludedEvents: List<IntegrationEventType>,
) : IntegrationConfigDto {
    constructor(integrationID: IntegrationID, config: DiscordNotificationConfig) : this(
        id = integrationID,
        type = integrationID.type,
        name = config.name,
        enabled = config.enabled,
        global = config.global,
        excludedEvents = config.excludedEvents.orEmpty(),
    )
}

@Introspected
data class PagerdutyConfigDto(
    override val id: IntegrationID,
    @param:Schema(description = IntegrationDocs.TYPE, required = true)
    override val type: IntegrationType,
    @param:Schema(description = IntegrationDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = IntegrationDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = IntegrationDocs.GLOBAL, required = true)
    override val global: Boolean,
    @param:Schema(description = IntegrationDocs.EXCLUDED_EVENTS, required = true)
    override val excludedEvents: List<IntegrationEventType>,
) : IntegrationConfigDto {
    constructor(integrationID: IntegrationID, config: PagerdutyConfig) : this(
        id = integrationID,
        type = integrationID.type,
        name = config.name,
        enabled = config.enabled,
        global = config.global,
        excludedEvents = config.excludedEvents.orEmpty(),
    )
}

@Introspected
data class EmailNotificationConfigDto(
    override val id: IntegrationID,
    @param:Schema(description = IntegrationDocs.TYPE, required = true)
    override val type: IntegrationType,
    @param:Schema(description = IntegrationDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = IntegrationDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = IntegrationDocs.GLOBAL, required = true)
    override val global: Boolean,
    @param:Schema(description = IntegrationDocs.EXCLUDED_EVENTS, required = true)
    override val excludedEvents: List<IntegrationEventType>,
    @param:Schema(description = "The email address from which notifications are sent", required = true)
    val fromAddress: String,
    @param:Schema(description = "The email address to which notifications are sent", required = true)
    val toAddress: String,
) : IntegrationConfigDto {
    constructor(integrationID: IntegrationID, config: EmailNotificationConfig) : this(
        id = integrationID,
        type = integrationID.type,
        name = config.name,
        enabled = config.enabled,
        global = config.global,
        excludedEvents = config.excludedEvents.orEmpty(),
        fromAddress = config.fromAddress,
        toAddress = config.toAddress,
    )
}

@Introspected
data class TelegramNotificationConfigDto(
    override val id: IntegrationID,
    @param:Schema(description = IntegrationDocs.TYPE, required = true)
    override val type: IntegrationType,
    @param:Schema(description = IntegrationDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = IntegrationDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = IntegrationDocs.GLOBAL, required = true)
    override val global: Boolean,
    @param:Schema(description = IntegrationDocs.EXCLUDED_EVENTS, required = true)
    override val excludedEvents: List<IntegrationEventType>,
    @param:Schema(description = "The chat ID for Telegram notifications", required = true)
    val chatId: String,
) : IntegrationConfigDto {
    constructor(integrationID: IntegrationID, config: TelegramNotificationConfig) : this(
        id = integrationID,
        type = integrationID.type,
        name = config.name,
        enabled = config.enabled,
        global = config.global,
        excludedEvents = config.excludedEvents.orEmpty(),
        chatId = config.chatId,
    )
}

@Introspected
data class WebhookNotificationConfigDto(
    override val id: IntegrationID,
    @param:Schema(description = IntegrationDocs.TYPE, required = true)
    override val type: IntegrationType,
    @param:Schema(description = IntegrationDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = IntegrationDocs.ENABLED, required = true)
    override val enabled: Boolean,
    @param:Schema(description = IntegrationDocs.GLOBAL, required = true)
    override val global: Boolean,
    @param:Schema(description = IntegrationDocs.EXCLUDED_EVENTS, required = true)
    override val excludedEvents: List<IntegrationEventType>,
    @param:Schema(description = "The target URL of the webhook", required = true)
    val url: String,
    @param:Schema(
        description = "The template for the webhook request body. If not provided, a generic payload will be sent",
        required = true,
    )
    val payloadTemplate: String?,
    @param:Schema(description = "The HTTP headers to include in the webhook request", required = true)
    val requestHeaders: Map<String, String>,
) : IntegrationConfigDto {
    constructor(integrationID: IntegrationID, config: WebhookNotificationConfig) : this(
        id = integrationID,
        type = integrationID.type,
        name = config.name,
        enabled = config.enabled,
        global = config.global,
        excludedEvents = config.excludedEvents.orEmpty(),
        url = config.url,
        payloadTemplate = config.payloadTemplate,
        requestHeaders = config.requestHeaders.orEmpty(),
    )
}
