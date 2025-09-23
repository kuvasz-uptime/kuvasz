package com.kuvaszuptime.kuvasz.models.settings

import java.net.URI

data class VersionInfo(
    val installedVersion: String,
    val latestVersion: String?,
    val latestVersionDetails: URI?,
) {
    val isUpToDate: Boolean = installedVersion == (latestVersion ?: installedVersion)
}
