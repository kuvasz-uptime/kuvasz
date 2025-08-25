package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.dto.LegacySettingsDto
import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import com.kuvaszuptime.kuvasz.models.handlers.DiscordNotificationConfig
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

    @Suppress("MaxLineLength")
    @Deprecated("Use getSettings() which returns the new SettingsDto")
    fun getLegacySettings(): LegacySettingsDto =
        LegacySettingsDto(
            authentication = LegacySettingsDto.AuthenticationSettingsDto(
                enabled = appGlobals.isAuthEnabled,
                accessTokenMaxAge = accessTokenMaxAge
            ),
            app = LegacySettingsDto.AppSettingsDto(
                version = appGlobals.appVersion,
                eventDataRetentionDays = appConfig.eventDataRetentionDays,
                latencyDataRetentionDays = appConfig.latencyDataRetentionDays,
                language = appConfig.language,
                eventLoggingEnabled = appConfig.logEventHandler,
                readOnlyMode = appConfig.isHttpMonitorExternalWriteDisabled(),
            ),
            integrations = LegacySettingsDto.IntegrationSettingsDto(
                smtp = smtpMailerConfig?.let { smtpConfig ->
                    LegacySettingsDto.SmtpConfigDto(
                        host = smtpConfig.host.orEmpty(),
                        port = smtpConfig.port ?: 0,
                        transportStrategy = smtpConfig.transportStrategy.toString()
                    )
                },
                slack = getIntegrationConfigs<SlackNotificationConfig, LegacySettingsDto.SlackNotificationConfigDto> { id, config ->
                    LegacySettingsDto.SlackNotificationConfigDto(id, config)
                },
                discord = getIntegrationConfigs<DiscordNotificationConfig, LegacySettingsDto.DiscordNotificationConfigDto> { id, config ->
                    LegacySettingsDto.DiscordNotificationConfigDto(id, config)
                },
                telegram = getIntegrationConfigs<TelegramNotificationConfig, LegacySettingsDto.TelegramNotificationConfigDto> { id, config ->
                    LegacySettingsDto.TelegramNotificationConfigDto(id, config)
                },
                email = getIntegrationConfigs<EmailNotificationConfig, LegacySettingsDto.EmailNotificationConfigDto> { id, config ->
                    LegacySettingsDto.EmailNotificationConfigDto(id, config)
                },
                pagerduty = getIntegrationConfigs<PagerdutyConfig, LegacySettingsDto.PagerdutyConfigDto> { id, config ->
                    LegacySettingsDto.PagerdutyConfigDto(id, config)
                }
            ),
            metricsExport = legacyMetricsExportSettingsDto()
        )

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
                editabilityState = SettingsDto.AppSettingsDto.EditabilityStateDto(
                    areHttpMonitorsReadOnly = appConfig.isHttpMonitorExternalWriteDisabled()
                )
            ),
            smtp = smtpMailerConfig?.let { smtpConfig ->
                SettingsDto.SmtpConfigDto(
                    host = smtpConfig.host.orEmpty(),
                    port = smtpConfig.port ?: 0,
                    transportStrategy = smtpConfig.transportStrategy.toString()
                )
            },
            metricsExport = metricsExportSettingsDto()
        )

    private fun legacyMetricsExportSettingsDto() = LegacySettingsDto.MetricsExportSettingsDto(
        exportEnabled = metricsExportEnabled,
        meters = LegacySettingsDto.MetricsExportSettingsDto.MeterSettingsDto(
            sslExpiry = exportConfig.sslExpiry,
            latestLatency = exportConfig.httpLatestLatency,
            uptimeStatus = exportConfig.httpUptimeStatus,
            sslStatus = exportConfig.sslStatus,
        ),
        exporters = LegacySettingsDto.MetricsExportSettingsDto.ExporterSettingsDto(
            prometheus = LegacySettingsDto.MetricsExportSettingsDto.ExporterSettingsDto.PrometheusSettingsDto(
                enabled = prometheusSettings.exportEnabled,
                descriptions = prometheusSettings.descriptionsEnabled,
            ),
            openTelemetry = LegacySettingsDto.MetricsExportSettingsDto.ExporterSettingsDto.OTLPSettingsDto(
                enabled = otlpSettings.exportEnabled,
                url = otlpSettings.url,
                step = otlpSettings.step,
            )
        )
    )

    private fun metricsExportSettingsDto() = SettingsDto.MetricsExportSettingsDto(
        exportEnabled = metricsExportEnabled,
        meters = SettingsDto.MetricsExportSettingsDto.MeterSettingsDto(
            sslExpiry = exportConfig.sslExpiry,
            httpLatestLatency = exportConfig.httpLatestLatency,
            httpUptimeStatus = exportConfig.httpUptimeStatus,
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

    private inline fun <reified C : IntegrationConfig, T> getIntegrationConfigs(
        transform: (IntegrationID, C) -> T
    ): List<T> where T : LegacySettingsDto.IntegrationConfigDto =
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
