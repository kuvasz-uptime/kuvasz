package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.models.dto.settings.SettingsDto
import com.kuvaszuptime.kuvasz.models.dto.settings.VersionInfoDto
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AppSettingsSchema(
    val app: ApplicationSettingsSchema,
    val versionInfo: VersionInfoSchema,
) {
    companion object {
        fun fromDto(dto: SettingsDto) = AppSettingsSchema(
            app = ApplicationSettingsSchema.fromDto(dto.app),
            versionInfo = VersionInfoSchema.fromDto(dto.versionInfo),
        )
    }
}

@Introspected
data class ApplicationSettingsSchema(
    val version: String,
    val eventDataRetentionDays: Int,
    val latencyDataRetentionDays: Int,
    val language: String,
    val eventLoggingEnabled: Boolean,
    val updateChecksEnabled: Boolean,
    val httpCheckTimeoutSeconds: Long,
    val editabilityState: EditabilityStateSchema,
) {
    companion object {
        fun fromDto(dto: SettingsDto.AppSettingsDto) = ApplicationSettingsSchema(
            version = dto.version,
            eventDataRetentionDays = dto.eventDataRetentionDays,
            latencyDataRetentionDays = dto.latencyDataRetentionDays,
            language = dto.language,
            eventLoggingEnabled = dto.eventLoggingEnabled,
            updateChecksEnabled = dto.updateChecksEnabled,
            httpCheckTimeoutSeconds = dto.httpCheckTimeoutSeconds,
            editabilityState = EditabilityStateSchema.fromDto(dto.editabilityState),
        )
    }
}

@Introspected
data class EditabilityStateSchema(
    val areHttpMonitorsReadOnly: Boolean,
    val arePushMonitorsReadOnly: Boolean,
    val areIcmpMonitorsReadOnly: Boolean,
    val areStatusPagesReadOnly: Boolean,
) {
    companion object {
        fun fromDto(dto: SettingsDto.AppSettingsDto.EditabilityStateDto) = EditabilityStateSchema(
            areHttpMonitorsReadOnly = dto.areHttpMonitorsReadOnly,
            arePushMonitorsReadOnly = dto.arePushMonitorsReadOnly,
            areIcmpMonitorsReadOnly = dto.areIcmpMonitorsReadOnly,
            areStatusPagesReadOnly = dto.areStatusPagesReadOnly,
        )
    }
}

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class VersionInfoSchema(
    val installedVersion: String,
    val latestVersion: String?,
    val latestVersionDetails: String?,
    val isUpToDate: Boolean,
) {
    companion object {
        fun fromDto(dto: VersionInfoDto) = VersionInfoSchema(
            installedVersion = dto.installedVersion,
            latestVersion = dto.latestVersion,
            latestVersionDetails = dto.latestVersionDetails?.toString(),
            isUpToDate = dto.isUpToDate,
        )
    }
}
