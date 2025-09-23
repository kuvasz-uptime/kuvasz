package com.kuvaszuptime.kuvasz.models.dto.settings

import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.net.URI

@Introspected
data class VersionInfoDto(
    @param:Schema(description = "The currently installed version of Kuvasz")
    val installedVersion: String,

    @param:Schema(description = "The latest available version of Kuvasz, if update checks are enabled", required = true)
    val latestVersion: String?,

    @param:Schema(
        description = "A link to the changelog or release notes for the latest version, if update checks are enabled",
        required = true,
    )
    val latestVersionDetails: URI?,

    @param:Schema(
        description = "Whether the installed version is up to date. If update checks are disabled, it's always true",
        required = true,
    )
    val isUpToDate: Boolean,
) {

    companion object {
        fun fromVersionInfo(versionInfo: VersionInfo) = VersionInfoDto(
            installedVersion = versionInfo.installedVersion,
            latestVersion = versionInfo.latestVersion,
            latestVersionDetails = versionInfo.latestVersionDetails,
            isUpToDate = versionInfo.isUpToDate,
        )
    }
}
