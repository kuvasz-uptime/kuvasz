package com.kuvaszuptime.kuvasz.repositories

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.SMTPMailerConfig
import com.kuvaszuptime.kuvasz.metrics.MetricsExportConfig
import com.kuvaszuptime.kuvasz.models.dto.settings.SettingsDto
import com.kuvaszuptime.kuvasz.models.dto.settings.VersionInfoDto
import com.kuvaszuptime.kuvasz.services.VersionChecker
import io.micronaut.context.annotation.Property
import jakarta.inject.Singleton

@Singleton
@Suppress("ProtectedMemberInFinalClass")
class SettingsRepository(
    private val appGlobals: AppGlobals,
    private val appConfig: AppConfig,
    private val smtpMailerConfig: SMTPMailerConfig?,
    private val exportConfig: MetricsExportConfig,
    private val prometheusSettings: PrometheusSettingsRepository,
    private val otlpSettings: OTLPSettingsRepository,
    private val versionChecker: VersionChecker,
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
                editabilityState = SettingsDto.AppSettingsDto.EditabilityStateDto(
                    areHttpMonitorsReadOnly = appConfig.isHttpMonitorExternalWriteDisabled(),
                    arePushMonitorsReadOnly = appConfig.isPushMonitorExternalWriteDisabled(),
                    areStatusPagesReadOnly = appConfig.isStatusPageExternalWriteDisabled(),
                ),
                updateChecksEnabled = appConfig.checkUpdates,
                httpCheckTimeoutSeconds = appConfig.httpCheckTimeoutSeconds,
            ),
            smtp = smtpMailerConfig?.let { smtpConfig ->
                SettingsDto.SmtpConfigDto(
                    host = smtpConfig.host.orEmpty(),
                    port = smtpConfig.port ?: 0,
                    transportStrategy = smtpConfig.transportStrategy.toString()
                )
            },
            metricsExport = metricsExportSettingsDto(),
            versionInfo = VersionInfoDto.fromVersionInfo(versionChecker.getVersionInfo()),
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
