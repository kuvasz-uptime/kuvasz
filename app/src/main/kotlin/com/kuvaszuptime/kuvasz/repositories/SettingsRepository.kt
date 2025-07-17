package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
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
    private val exportConfig: MetricsExportConfig,
    private val prometheusSettings: PrometheusSettingsRepository,
    private val otlpSettings: OTLPSettingsRepository,
) {

    @field:Property(name = "micronaut.security.token.generator.access-token.expiration")
    protected var accessTokenMaxAge: Long = 0L

    @field:Property(name = "micronaut.metrics.enabled")
    protected var metricsExportEnabled: Boolean = false

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
            ),
            metricsExport = SettingsDto.MetricsExportSettingsDto(
                exportEnabled = metricsExportEnabled,
                meters = SettingsDto.MetricsExportSettingsDto.MeterSettingsDto(
                    sslExpiry = exportConfig.sslExpiry,
                    latestLatency = exportConfig.latestLatency,
                    uptimeStatus = exportConfig.uptimeStatus,
                    sslStatus = exportConfig.sslStatus,
                ),
                exporters = SettingsDto.MetricsExportSettingsDto.ExporterSettingsDto(
                    prometheus = SettingsDto.MetricsExportSettingsDto.ExporterSettingsDto.PrometheusSettingsDto(
                        enabled = prometheusSettings.exportEnabled,
                        descriptions = prometheusSettings.descriptionsEnabled,
                    ),
                    openTelemetry = SettingsDto.MetricsExportSettingsDto.ExporterSettingsDto.OTLPSettingsDto(
                        enabled = otlpSettings.exportEnabled,
                        url = otlpSettings.url,
                        step = otlpSettings.step,
                    )
                )
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

@Singleton
class PrometheusSettingsRepository {

    @field:Property(name = "micronaut.metrics.export.prometheus.enabled")
    var exportEnabled: Boolean = false

    @field:Property(name = "micronaut.metrics.export.prometheus.descriptions")
    var descriptionsEnabled: Boolean = false
}

@Singleton
class OTLPSettingsRepository {

    @field:Property(name = "micronaut.metrics.export.otlp.enabled")
    var exportEnabled: Boolean = false

    @field:Property(name = "micronaut.metrics.export.otlp.url")
    var url: String = ""

    @field:Property(name = "micronaut.metrics.export.otlp.step")
    var step: String = ""
}
