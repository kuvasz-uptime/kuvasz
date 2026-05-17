package com.kuvaszuptime.kuvasz.models.dto.settings

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class SettingsDto(
    @param:Schema(description = "Authentication settings", required = true)
    val authentication: AuthenticationSettingsDto,
    @param:Schema(description = "Application settings", required = true)
    val app: AppSettingsDto,
    @param:Schema(description = "Metrics exporter settings", required = true)
    val metricsExport: MetricsExportSettingsDto,
    @param:Schema(description = "SMTP configuration for email notifications", required = false, nullable = true)
    val smtp: SmtpConfigDto?,
    val versionInfo: VersionInfoDto
) {
    @Introspected
    data class AuthenticationSettingsDto(
        @param:Schema(description = "Whether authentication is enabled", required = true)
        val enabled: Boolean,
        @param:Schema(description = "The maximum age of the access token in seconds", required = true)
        val accessTokenMaxAge: Long,
    )

    @Introspected
    data class AppSettingsDto(
        @param:Schema(description = "The version of the application", required = true)
        val version: String,
        @param:Schema(description = "Number of days to retain event data", required = true)
        val eventDataRetentionDays: Int,
        @param:Schema(description = "Number of days to retain latency data", required = true)
        val latencyDataRetentionDays: Int,
        @param:Schema(description = "The language of the application", required = true)
        val language: String,
        @param:Schema(description = "Whether event logging is enabled", required = true)
        val eventLoggingEnabled: Boolean,
        @param:Schema(
            description = "Whether the application is in read-only mode (i.e. monitors are configured via YAML",
            required = true,
        )
        val editabilityState: EditabilityStateDto,
        @param:Schema(description = "Whether automatic update checks are enabled", required = true)
        val updateChecksEnabled: Boolean,
        @param:Schema(description = "The HTTP uptime checks' read timeout", required = true)
        val httpCheckTimeoutSeconds: Long,
    ) {
        data class EditabilityStateDto(
            @param:Schema(description = "Whether the HTTP monitors are in read-only mode", required = true)
            val areHttpMonitorsReadOnly: Boolean,

            @param:Schema(description = "Whether the status pages are in read-only mode", required = true)
            val areStatusPagesReadOnly: Boolean,

            @param:Schema(description = "Whether the push monitors are in read-only mode", required = true)
            val arePushMonitorsReadOnly: Boolean,

            @param:Schema(description = "Whether the ICMP monitors are in read-only mode", required = true)
            val areIcmpMonitorsReadOnly: Boolean,
        )
    }

    @Introspected
    data class SmtpConfigDto(
        @param:Schema(description = "The SMTP host", required = true)
        val host: String,
        @param:Schema(description = "The SMTP port", required = true)
        val port: Int,
        @param:Schema(description = "The SMTP transport strategy", required = true)
        val transportStrategy: String,
    )

    @Introspected
    data class MetricsExportSettingsDto(
        @param:Schema(description = "Whether the metrics exporting is generally enabled", required = true)
        val exportEnabled: Boolean,
        @param:Schema(description = "Settings for individual meters", required = true)
        val meters: MeterSettingsDto,
        @param:Schema(description = "Settings for individual exporters", required = true)
        val exporters: ExporterSettingsDto,
    ) {
        @Introspected
        data class MeterSettingsDto(
            @param:Schema(description = "Whether SSL certificate expiry exporter is enabled", required = true)
            val sslExpiry: Boolean,
            @param:Schema(description = "Whether HTTP latest latency exporter is enabled", required = true)
            val httpLatestLatency: Boolean,
            @param:Schema(description = "Whether HTTP monitor status exporter is enabled", required = true)
            val httpUptimeStatus: Boolean,
            @param:Schema(description = "Whether SSL status exporter is enabled", required = true)
            val sslStatus: Boolean,
            @param:Schema(description = "Whether push monitor status exporter is enabled", required = true)
            val pushUptimeStatus: Boolean,
            @param:Schema(description = "Whether ICMP monitor status exporter is enabled", required = true)
            val icmpUptimeStatus: Boolean,
            @param:Schema(description = "Whether ICMP latest latency exporter is enabled", required = true)
            val icmpLatestLatency: Boolean,
            @param:Schema(description = "Whether ICMP latest packet loss exporter is enabled", required = true)
            val icmpLatestPacketLoss: Boolean,
        )

        @Introspected
        data class ExporterSettingsDto(
            @param:Schema(description = "Prometheus exporter settings", required = true)
            val prometheus: PrometheusSettingsDto,
            @param:Schema(description = "OpenTelemetry exporter settings", required = true)
            val openTelemetry: OTLPSettingsDto,
        ) {
            @Introspected
            data class PrometheusSettingsDto(
                @param:Schema(description = "Whether the exporter is enabled", required = true)
                val enabled: Boolean,
                @param:Schema(description = "Whether descriptions are included in the export", required = true)
                val descriptions: Boolean,
            )

            @Introspected
            data class OTLPSettingsDto(
                @param:Schema(description = "Whether the exporter is enabled", required = true)
                val enabled: Boolean,
                @param:Schema(description = "The endpoint where the metrics will be published", required = true)
                val url: String,
                @param:Schema(
                    description = "The step for the metrics reporting as an ISO 8601 duration string",
                    required = true,
                )
                val step: String,
            )
        }
    }
}
