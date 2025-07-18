package com.kuvaszuptime.kuvasz.models.handlers

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

    @get:NotBlank
    val integrationKey: String

    companion object {
        const val IDENTIFIER = "pagerduty"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(EmailNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface EmailNotificationConfig : IntegrationConfig {

    @get:NotBlank
    val toAddress: String

    @get:NotBlank
    val fromAddress: String

    companion object {
        const val IDENTIFIER = "email"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(SlackNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface SlackNotificationConfig : IntegrationConfig {

    @get:NotBlank
    val webhookUrl: String

    companion object {
        const val IDENTIFIER = "slack"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(DiscordNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface DiscordNotificationConfig : IntegrationConfig {

    @get:NotBlank
    val webhookUrl: String

    companion object {
        const val IDENTIFIER = "discord"
        const val CONFIG_PREFIX = "${IntegrationConfig.CONFIG_PREFIX}.$IDENTIFIER"
    }
}

@EachProperty(TelegramNotificationConfig.CONFIG_PREFIX, list = true)
@Introspected
interface TelegramNotificationConfig : IntegrationConfig {

    @get:NotBlank
    val apiToken: String

    @get:NotBlank
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
