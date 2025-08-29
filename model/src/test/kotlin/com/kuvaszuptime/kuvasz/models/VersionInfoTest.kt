package com.kuvaszuptime.kuvasz.models

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class VersionInfoTest : StringSpec({

    "isUpToDate is true when installedVersion equals latestVersion" {
        val versionInfo = VersionInfo(
            installedVersion = "1.0.0",
            latestVersion = "1.0.0",
            latestVersionDetails = null
        )
        versionInfo.isUpToDate.shouldBeTrue()
    }

    "isUpToDate is false when installedVersion does not equal latestVersion" {
        val versionInfo = VersionInfo(
            installedVersion = "1.0.0",
            latestVersion = "1.1.0",
            latestVersionDetails = null
        )
        versionInfo.isUpToDate.shouldBeFalse()
    }

    "isUpToDate is true when latestVersion is null" {
        val versionInfo = VersionInfo(
            installedVersion = "1.0.0",
            latestVersion = null,
            latestVersionDetails = null
        )
        versionInfo.isUpToDate.shouldBeTrue()
    }
})
