package com.kuvaszuptime.kuvasz.services

import com.fasterxml.jackson.annotation.JsonProperty
import com.kuvaszuptime.kuvasz.buildconfig.BuildConfig
import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import com.kuvaszuptime.kuvasz.services.check.http.HttpCheckRequestConfigurator
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.retry.annotation.Retryable
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.URI

@Singleton
@Requires(property = "app-config.check-updates", value = StringUtils.TRUE)
class VersionCheckScheduler(private val versionChecker: VersionChecker) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedDelay = "12h", initialDelay = "2s")
    fun scheduleVersionCheck() {
        logger.debug("Checking for application updates...")
        runBlocking {
            versionChecker.checkForUpdates()
            val versionInfo = versionChecker.getVersionInfo()
            logger.debug(
                "Version check completed. Installed version: ${versionInfo.installedVersion}, " +
                    "Latest stable version: ${versionInfo.latestVersion}"
            )
        }
    }
}

@Singleton
class VersionChecker(private val gitHubClient: GitHubClient) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val installedVersion = BuildConfig.APP_VERSION
    private var latestVersion: String? = null
    private var latestVersionDetails: URI? = null

    fun getVersionInfo() = VersionInfo(
        installedVersion = installedVersion,
        latestVersion = latestVersion,
        latestVersionDetails = latestVersionDetails,
    )

    /**
     * Checks GitHub for the latest release version and updates internal state.
     * Skips pre-releases.
     */
    suspend fun checkForUpdates() {
        try {
            val latestRelease = gitHubClient.getLatestRelease()
            if (latestRelease.prerelease) {
                logger.debug("Latest release of Kuvasz [${latestRelease.tagName}] is a pre-release, skipping")
                return
            }
            latestVersion = latestRelease.tagName
            latestVersionDetails = (CHANGELOG_BASE_URL + latestRelease.tagName).toUri()
        } catch (ex: HttpClientException) {
            logger.error("Failed to check for updates via GitHub, error: ${ex.message}")
        }
    }

    companion object {
        private const val CHANGELOG_BASE_URL = "https://kuvasz-uptime.dev/changelog#"
    }
}

@Client(GitHubClient.BASE_URL)
@Retryable
interface GitHubClient {

    @Get(LATEST_RELEASE_PATH)
    @Header(name = HttpHeaders.USER_AGENT, value = HttpCheckRequestConfigurator.USER_AGENT)
    suspend fun getLatestRelease(): GitHubRelease

    companion object {
        const val BASE_URL = "https://api.github.com"
        const val LATEST_RELEASE_PATH = "/repos/kuvasz-uptime/kuvasz/releases/latest"
    }
}

/**
 * A minimal set of fields we need from the GitHub release API
 * See: https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#get-the-latest-release
 */
@Introspected
data class GitHubRelease(
    @field:JsonProperty("tag_name")
    val tagName: String,
    val prerelease: Boolean,
)
