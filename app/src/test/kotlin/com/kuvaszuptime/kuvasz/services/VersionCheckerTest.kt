package com.kuvaszuptime.kuvasz.services

import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.annotation.Ignored
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.micronaut.context.annotation.Property
import io.micronaut.core.util.StringUtils
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@MicronautTest(startApplication = false)
class VersionCheckerTest(
    gitHubClient: GitHubClient,
    versionChecker: VersionChecker,
) : ShouldSpec({

    context("the update checker logic") {

        should("handle it gracefully if the client returns an error") {

            val mockClient = getMock(gitHubClient)
            coEvery { mockClient.getLatestRelease() } throws HttpClientException("Simulated failure")
            val versionInfoBefore = versionChecker.getVersionInfo()

            shouldNotThrowAny { versionChecker.checkForUpdates() }

            val versionInfoAfter = versionChecker.getVersionInfo()

            versionInfoAfter shouldBe versionInfoBefore
        }

        should("ignore pre-releases") {

            val mockClient = getMock(gitHubClient)
            coEvery {
                mockClient.getLatestRelease()
            } returns GitHubRelease(
                tagName = "999.999.999-beta",
                prerelease = true,
            )
            val versionInfoBefore = versionChecker.getVersionInfo()
            versionInfoBefore.latestVersion.shouldBeNull()
            versionInfoBefore.latestVersionDetails.shouldBeNull()
            versionInfoBefore.isUpToDate.shouldBeTrue()

            shouldNotThrowAny { versionChecker.checkForUpdates() }

            val versionInfoAfter = versionChecker.getVersionInfo()
            versionInfoAfter shouldBe versionInfoBefore
        }

        should("process stable releases") {

            val mockClient = getMock(gitHubClient)
            coEvery {
                mockClient.getLatestRelease()
            } returns GitHubRelease(
                tagName = "999.999.999",
                prerelease = false,
            )

            val versionInfoBefore = versionChecker.getVersionInfo()
            versionInfoBefore.latestVersion.shouldBeNull()
            versionInfoBefore.latestVersionDetails.shouldBeNull()
            versionInfoBefore.isUpToDate.shouldBeTrue()

            shouldNotThrowAny { versionChecker.checkForUpdates() }

            val versionInfoAfter = versionChecker.getVersionInfo()
            versionInfoAfter.installedVersion shouldBe versionInfoBefore.installedVersion
            versionInfoAfter.latestVersion shouldBe "999.999.999"
            versionInfoAfter.latestVersionDetails shouldBe "https://kuvasz-uptime.dev/changelog#999.999.999".toUri()
            versionInfoAfter.isUpToDate.shouldBeFalse()
        }
    }
}) {
    @MockBean(GitHubClient::class)
    fun mockGitHubClient(): GitHubClient = mockk()
}

@MicronautTest(startApplication = false)
@Ignored
class VersionCheckerE2ETest(versionChecker: VersionChecker) : ShouldSpec({

    context("the update checker logic") {

        should("be able to call and parse the real response from GitHub") {
            val versionBeforeCheck = versionChecker.getVersionInfo()
            versionBeforeCheck.installedVersion.shouldNotBeEmpty()
            versionBeforeCheck.latestVersion.shouldBeNull()
            versionBeforeCheck.latestVersionDetails.shouldBeNull()
            versionBeforeCheck.isUpToDate.shouldBeTrue()

            // Do the real check over HTTP
            versionChecker.checkForUpdates()

            val versionAfterCheck = versionChecker.getVersionInfo()
            versionAfterCheck.installedVersion shouldBe versionBeforeCheck.installedVersion
            versionAfterCheck.latestVersion.shouldNotBeEmpty()
            versionAfterCheck.latestVersionDetails.shouldNotBeNull()
        }
    }
})

@MicronautTest(startApplication = false)
@Property(name = "app-config.check-updates", value = StringUtils.TRUE)
class VersionCheckSchedulerTest(gitHubClient: GitHubClient) : StringSpec({

    "the version check should be scheduled after the app starts" {

        val mockClient = getMock(gitHubClient)
        coEvery {
            mockClient.getLatestRelease()
        } returns GitHubRelease(
            tagName = "999.999.999",
            prerelease = false,
        )

        // Wait a bit to let the scheduler kick in (it has a 2s initial delay)
        delay(1000)
        eventually(5.seconds) {
            coVerify(exactly = 1) { mockClient.getLatestRelease() }
        }
    }
}) {
    @MockBean(GitHubClient::class)
    fun mockGitHubClient(): GitHubClient = mockk()
}
