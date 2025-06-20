package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import com.kuvaszuptime.kuvasz.models.handlers.EmailNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.handlers.PagerdutyConfig
import com.kuvaszuptime.kuvasz.models.handlers.SlackNotificationConfig
import com.kuvaszuptime.kuvasz.models.handlers.TelegramNotificationConfig
import com.kuvaszuptime.kuvasz.services.IntegrationRepository
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton

@Singleton
@Suppress("ProtectedMemberInFinalClass")
class SettingsRepository(
    private val integrationRepository: IntegrationRepository,
    private val appGlobals: AppGlobals,
    private val appConfig: AppConfig,
    private val smtpMailerConfig: SMTPMailerConfig?,
) {

    @field:Property(name = "micronaut.security.token.generator.access-token.expiration")
    protected var accessTokenMaxAge: Long = 0L

    fun getSettings(): SettingsDto =
        SettingsDto(
            authentication = SettingsDto.AuthenticationSettingsDto(
                enabled = appGlobals.isAuthEnabled,
                accessTokenMaxAge = accessTokenMaxAge
            ),
            app = SettingsDto.AppSettingsDto(
                version = appGlobals.appVersion,
                eventDataRetentionDays = appConfig.eventDataRetentionDays,
                latencyDataRetentionDays = appConfig.latencyDataRetentionDays,
                language = appConfig.language,
                eventLoggingEnabled = appConfig.logEventHandler,
                readOnlyMode = appConfig.isExternalWriteDisabled(),
            ),
            integrations = SettingsDto.IntegrationSettingsDto(
                smtp = smtpMailerConfig?.let { smtpConfig ->
                    SettingsDto.SmtpConfigDto(
                        host = smtpConfig.host.orEmpty(),
                        port = smtpConfig.port ?: 0,
                        transportStrategy = smtpConfig.transportStrategy.toString()
                    )
                },
                slack = getIntegrationConfigs<SlackNotificationConfig, SettingsDto.SlackNotificationConfigDto>
                { id, config ->
                    SettingsDto.SlackNotificationConfigDto(id, config)
                },
                telegram = getIntegrationConfigs<TelegramNotificationConfig, SettingsDto.TelegramNotificationConfigDto>
                { id, config ->
                    SettingsDto.TelegramNotificationConfigDto(id, config)
                },
                email = getIntegrationConfigs<EmailNotificationConfig, SettingsDto.EmailNotificationConfigDto>
                { id, config ->
                    SettingsDto.EmailNotificationConfigDto(id, config)
                },
                pagerduty = getIntegrationConfigs<PagerdutyConfig, SettingsDto.PagerdutyConfigDto>
                { id, config ->
                    SettingsDto.PagerdutyConfigDto(id, config)
                }
            )
        )

    private inline fun <reified C : IntegrationConfig, T> getIntegrationConfigs(
        transform: (IntegrationID, C) -> T
    ): List<T> where T : SettingsDto.IntegrationConfigDto =
        integrationRepository.configuredIntegrations.mapNotNull { (id, config) ->
            if (config is C) {
                transform(id, config)
            } else {
                null
            }
        }
}
