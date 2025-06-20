package com.kuvaszuptime.kuvasz.models.dto

import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import io.micronaut.core.annotation.Introspected

@Introspected
data class SettingsDto(
    val authentication: AuthenticationSettingsDto,
    val app: AppSettingsDto,
    val integrations: IntegrationSettingsDto,
) {
    @Introspected
    data class AuthenticationSettingsDto(
        val enabled: Boolean,
        val accessTokenMaxAge: Long,
    )

    @Introspected
    data class AppSettingsDto(
        val version: String,
        val eventDataRetentionDays: Int,
        val latencyDataRetentionDays: Int,
        val language: String,
        val eventLoggingEnabled: Boolean,
        val readOnlyMode: Boolean,
    )

    @Introspected
    data class IntegrationSettingsDto(
        val smtp: SmtpConfigDto?,
        val slack: List<SlackNotificationConfigDto>,
        val pagerduty: List<PagerdutyConfigDto>,
        val email: List<EmailNotificationConfigDto>,
        val telegram: List<TelegramNotificationConfigDto>,
    )

    @Introspected
    data class SmtpConfigDto(
        val host: String,
        val port: Int,
        val transportStrategy: String,
    )

    interface IntegrationConfigDto {
        val id: IntegrationID
        val type: IntegrationType
        val name: String
        val enabled: Boolean
        val global: Boolean
    }

    @Introspected
    data class SlackNotificationConfigDto(
        override val id: IntegrationID,
        override val type: IntegrationType,
        override val name: String,
        override val enabled: Boolean,
        override val global: Boolean,
    ) : IntegrationConfigDto {
        constructor(integrationID: IntegrationID, config: SlackNotificationConfig) : this(
            id = integrationID,
            type = integrationID.type,
            name = config.name,
            enabled = config.enabled,
            global = config.global,
        )
    }

    @Introspected
    data class PagerdutyConfigDto(
        override val id: IntegrationID,
        override val type: IntegrationType,
        override val name: String,
        override val enabled: Boolean,
        override val global: Boolean,
    ) : IntegrationConfigDto {
        constructor(integrationID: IntegrationID, config: PagerdutyConfig) : this(
            id = integrationID,
            type = integrationID.type,
            name = config.name,
            enabled = config.enabled,
            global = config.global,
        )
    }

    @Introspected
    data class EmailNotificationConfigDto(
        override val id: IntegrationID,
        override val type: IntegrationType,
        override val name: String,
        override val enabled: Boolean,
        override val global: Boolean,
        val fromAddress: String,
        val toAddress: String,
    ) : IntegrationConfigDto {
        constructor(integrationID: IntegrationID, config: EmailNotificationConfig) : this(
            id = integrationID,
            type = integrationID.type,
            name = config.name,
            enabled = config.enabled,
            global = config.global,
            fromAddress = config.fromAddress,
            toAddress = config.toAddress,
        )
    }

    @Introspected
    data class TelegramNotificationConfigDto(
        override val id: IntegrationID,
        override val type: IntegrationType,
        override val name: String,
        override val enabled: Boolean,
        override val global: Boolean,
        val chatId: String,
    ) : IntegrationConfigDto {
        constructor(integrationID: IntegrationID, config: TelegramNotificationConfig) : this(
            id = integrationID,
            type = integrationID.type,
            name = config.name,
            enabled = config.enabled,
            global = config.global,
            chatId = config.chatId,
        )
    }
}
