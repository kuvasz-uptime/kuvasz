package com.kuvaszuptime.kuvasz.models.handlers

import com.kuvaszuptime.kuvasz.models.dto.IntegrationValidationMessages
import com.kuvaszuptime.kuvasz.validation.ValidHeaderNames
import io.micronaut.context.annotation.EachProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.bind.annotation.Bindable
import jakarta.validation.constraints.NotBlank

sealed interface IntegrationConfig {
    val name: String

    @get:Bindable(defaultValue = "true")
    val enabled: Boolean

    @get:Bindable(defaultValue = "false")
    val global: Boolean

    val excludedEvents: List<IntegrationEventType>?

    companion object {
        const val CONFIG_PREFIX = "integrations"
    }
}

@EachProperty(PagerdutyConfig.CONFIG_PREFIX, list = true)
@Introspected
interface PagerdutyConfig : IntegrationConfig {

    @get:NotBlank(message = IntegrationValidationMessages.PAGERDUTY_KEY_NOT_BLANK)
    val integrationKey: String

    companion object {
        const val IDENTIFIER = "pagerduty"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(EmailNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface EmailNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = IntegrationValidationMessages.EMAIL_INTEGRATION_TO_NOT_BLANK)
    val toAddress: String

    @get:NotBlank(message = IntegrationValidationMessages.EMAIL_INTEGRATION_FROM_NOT_BLANK)
    val fromAddress: String

    companion object {
        const val IDENTIFIER = "email"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(SlackNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface SlackNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = IntegrationValidationMessages.SLACK_WEBHOOK_URL_NOT_BLANK)
    val webhookUrl: String

    companion object {
        const val IDENTIFIER = "slack"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(DiscordNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface DiscordNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = IntegrationValidationMessages.DISCORD_WEBHOOK_URL_NOT_BLANK)
    val webhookUrl: String

    companion object {
        const val IDENTIFIER = "discord"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(TelegramNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface TelegramNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = IntegrationValidationMessages.TELEGRAM_BOT_TOKEN_NOT_BLANK)
    val apiToken: String

    @get:NotBlank(message = IntegrationValidationMessages.TELEGRAM_CHAT_ID_NOT_BLANK)
    val chatId: String

    companion object {
        const val IDENTIFIER = "telegram"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(WebhookNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface WebhookNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = IntegrationValidationMessages.WEBHOOK_URL_NOT_BLANK)
    val url: String

    @get:ValidHeaderNames
    val requestHeaders: Map<String, String>?

    val payloadTemplate: String?

    @get:Bindable(defaultValue = "POST")
    val httpMethod: WebhookHttpMethod

    companion object {
        const val IDENTIFIER = "webhook"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

enum class WebhookHttpMethod {
    POST,
    PUT,
    PATCH,
    GET,
}

val IntegrationConfig.type: IntegrationType
    get() = when (this) {
        is EmailNotificationConfig -> IntegrationType.EMAIL
        is PagerdutyConfig -> IntegrationType.PAGERDUTY
        is SlackNotificationConfig -> IntegrationType.SLACK
        is TelegramNotificationConfig -> IntegrationType.TELEGRAM
        is DiscordNotificationConfig -> IntegrationType.DISCORD
        is WebhookNotificationConfig -> IntegrationType.WEBHOOK
    }

val IntegrationConfig.id: IntegrationID
    get() = IntegrationID(type = type, name = name)

typealias IntegrationMap = Map<IntegrationID, IntegrationConfig>
