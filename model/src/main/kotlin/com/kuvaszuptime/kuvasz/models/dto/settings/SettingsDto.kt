package com.kuvaszuptime.kuvasz.models.dto.settings

import com.kuvaszuptime.kuvasz.models.settings.ConnectivityState
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityStatus
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class SettingsDto(
    @param:Schema(description = "Authentication settings", required = true)
    val authentication: AuthenticationSettingsDto,
    @param:Schema(description = "Application settings", required = true)
    val app: AppSettingsDto,
    @param:Schema(description = "Metrics exporter settings", required = true)
    val metricsExport: MetricsExportSettingsDto,
    @param:Schema(description = "MCP server settings", required = true)
    val mcpServer: McpServerSettingsDto,
    @param:Schema(
        description = "The connectivity check's settings and live state, null when it is disabled",
        required = false,
        nullable = true,
    )
    val connectivityCheck: ConnectivityCheckSettingsDto?,
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
        @param:Schema(
            description = "The OIDC provider settings, present only when OIDC authentication is enabled",
            required = false,
            nullable = true,
        )
        val oidc: OidcSettingsDto?,
    ) {
        @Introspected
        data class OidcSettingsDto(
            @param:Schema(description = "The issuer URL of the configured OIDC provider", required = true)
            val issuer: String,
            @param:Schema(description = "The client ID registered with the OIDC provider", required = true)
            val clientId: String,
        )
    }

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
        @param:Schema(
            description = "The maximum number of redirects the HTTP uptime checks follow",
            required = true,
        )
        val httpCheckMaxRedirects: Int,
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

            @param:Schema(description = "Whether the TCP monitors are in read-only mode", required = true)
            val areTcpMonitorsReadOnly: Boolean,

            @param:Schema(description = "Whether the DNS monitors are in read-only mode", required = true)
            val areDnsMonitorsReadOnly: Boolean,
            @param:Schema(description = "Whether the Docker monitors are in read-only mode", required = true)
            val areDockerMonitorsReadOnly: Boolean,

            @param:Schema(description = "Whether the maintenance windows are in read-only mode", required = true)
            val areMaintenanceWindowsReadOnly: Boolean,
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
            @param:Schema(description = "Whether TCP monitor status exporter is enabled", required = true)
            val tcpUptimeStatus: Boolean,
            @param:Schema(description = "Whether TCP latest latency exporter is enabled", required = true)
            val tcpLatestLatency: Boolean,
            @param:Schema(description = "Whether DNS monitor status exporter is enabled", required = true)
            val dnsUptimeStatus: Boolean,
            @param:Schema(description = "Whether DNS latest latency exporter is enabled", required = true)
            val dnsLatestLatency: Boolean,
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

    @Introspected
    data class McpServerSettingsDto(
        @param:Schema(description = "Whether the MCP server is enabled", required = true)
        val enabled: Boolean,
    )

    @Introspected
    data class ConnectivityCheckSettingsDto(
        @param:Schema(description = "The endpoints that are dialed to decide about the connectivity", required = true)
        val targets: List<String>,
        @param:Schema(description = "How often the connectivity is probed, in seconds", required = true)
        val intervalSeconds: Long,
        @param:Schema(description = "The timeout of a single dial, in seconds", required = true)
        val timeoutSeconds: Long,
        @param:Schema(description = "The last known state of the outbound connectivity", required = true)
        val state: ConnectivityState,
        @param:Schema(
            description = "Whether the checks Kuvasz initiates on its own are currently suspended",
            required = true,
        )
        val checksSuspended: Boolean,
        @param:Schema(
            description = "When the connectivity was probed the last time", required = false,
            nullable = true
        )
        val lastCheckedAt: OffsetDateTime?,
        @param:Schema(
            description = "Since when the connectivity is considered to be lost", required = false,
            nullable = true
        )
        val downSince: OffsetDateTime?,
        @param:Schema(description = "The error of the last failed dial", required = false, nullable = true)
        val lastError: String?,
    ) {
        companion object {
            fun fromStatus(status: ConnectivityStatus) = ConnectivityCheckSettingsDto(
                targets = status.targets,
                intervalSeconds = status.intervalSeconds,
                timeoutSeconds = status.timeoutSeconds,
                state = status.state,
                checksSuspended = status.areChecksSuspended,
                lastCheckedAt = status.lastCheckedAt,
                downSince = status.downSince,
                lastError = status.lastError,
            )
        }
    }
}
