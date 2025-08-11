package com.kuvaszuptime.kuvasz.models.handlers

import com.kuvaszuptime.kuvasz.models.dto.ValidationMessages
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

    companion object {
        const val CONFIG_PREFIX = "integrations"
    }
}

@EachProperty(PagerdutyConfig.CONFIG_PREFIX, list = true)
@Introspected
interface PagerdutyConfig : IntegrationConfig {

    @get:NotBlank(message = ValidationMessages.PAGERDUTY_KEY_NOT_BLANK)
    val integrationKey: String

    companion object {
        const val IDENTIFIER = "pagerduty"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(EmailNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface EmailNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = ValidationMessages.EMAIL_INTEGRATION_TO_NOT_BLANK)
    val toAddress: String

    @get:NotBlank(message = ValidationMessages.EMAIL_INTEGRATION_FROM_NOT_BLANK)
    val fromAddress: String

    companion object {
        const val IDENTIFIER = "email"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(SlackNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface SlackNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = ValidationMessages.SLACK_WEBHOOK_URL_NOT_BLANK)
    val webhookUrl: String

    companion object {
        const val IDENTIFIER = "slack"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(DiscordNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface DiscordNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = ValidationMessages.DISCORD_WEBHOOK_URL_NOT_BLANK)
    val webhookUrl: String

    companion object {
        const val IDENTIFIER = "discord"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(TelegramNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface TelegramNotificationConfig : IntegrationConfig {

    @get:NotBlank(message = ValidationMessages.TELEGRAM_BOT_TOKEN_NOT_BLANK)
    val apiToken: String

    @get:NotBlank(message = ValidationMessages.TELEGRAM_CHAT_ID_NOT_BLANK)
    val chatId: String

    companion object {
        const val IDENTIFIER = "telegram"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

val IntegrationConfig.type: IntegrationType
    get() = when (this) {
        is EmailNotificationConfig -> IntegrationType.EMAIL
        is PagerdutyConfig -> IntegrationType.PAGERDUTY
        is SlackNotificationConfig -> IntegrationType.SLACK
        is TelegramNotificationConfig -> IntegrationType.TELEGRAM
        is DiscordNotificationConfig -> IntegrationType.DISCORD
    }

val IntegrationConfig.id: IntegrationID
    get() = IntegrationID(type = type, name = name)

typealias IntegrationMap = Map<IntegrationID, IntegrationConfig>
