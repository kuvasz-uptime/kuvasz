package com.kuvaszuptime.kuvasz.controllers.settings

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.models.dto.settings.SettingsDto
import com.kuvaszuptime.kuvasz.models.settings.VersionInfo
import com.kuvaszuptime.kuvasz.services.VersionChecker
import com.kuvaszuptime.kuvasz.testutils.SMTPTest
import com.kuvaszuptime.kuvasz.util.toUri
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactive.awaitFirst

@MicronautTest(
    environments = [
        "full-integrations-setup",
        "yaml-monitors",
        "yaml-push-monitors",
        "yaml-icmp-monitors",
        "enabled-metrics-otlp",
        "enabled-metrics-prometheus",
        "enabled-mcp-server",
        "status-pages",
        "maintenance-windows-readonly",
    ]
)
@SMTPTest
@Property(name = "micronaut.security.token.generator.access-token.expiration", value = "3600")
@Property(name = "app-config.event-data-retention-days", value = "5")
@Property(name = "app-config.latency-data-retention-days", value = "6")
@Property(name = "app-config.language", value = "en")
@Property(name = "app-config.log-event-handler", value = "true")
@Property(name = "app-config.http-check-timeout-seconds", value = "10")
class SettingsControllerTest(
    settingsClient: SettingsClient,
    appGlobals: AppGlobals,
    versionChecker: VersionChecker,
) : DatabaseBehaviorSpec({

    given("the SettingsController") {

        `when`("the getSettings method is called") {

            with(getMock(versionChecker)) {
                every { getVersionInfo() } returns VersionInfo(
                    installedVersion = appGlobals.appVersion,
                    latestVersion = "123.456.789",
                    latestVersionDetails = "https//something.com/details".toUri(),
                )
            }
            val result = settingsClient.getSettings()

            then("it should return the settings") {
                result.authentication.enabled shouldBe false
                result.authentication.accessTokenMaxAge shouldBe 3600L
                // OIDC is not enabled in this setup, so it should not be exposed
                result.authentication.oidc shouldBe null
                result.app.eventDataRetentionDays shouldBe 5
                result.app.latencyDataRetentionDays shouldBe 6
                result.app.language shouldBe "en"
                result.app.eventLoggingEnabled shouldBe true
                result.app.version.shouldNotBeEmpty() shouldBe appGlobals.appVersion
                result.app.editabilityState.areHttpMonitorsReadOnly shouldBe true
                result.app.editabilityState.arePushMonitorsReadOnly shouldBe true
                result.app.editabilityState.areIcmpMonitorsReadOnly shouldBe true
                result.app.editabilityState.areStatusPagesReadOnly shouldBe true
                result.app.editabilityState.areMaintenanceWindowsReadOnly shouldBe true
                result.app.updateChecksEnabled shouldBe false
                result.app.httpCheckTimeoutSeconds shouldBe 10

                with(result.smtp.shouldNotBeNull()) {
                    host shouldBe "localhost"
                    port shouldBeGreaterThan 0
                    transportStrategy shouldBe "SMTP"
                }

                with(result.metricsExport) {
                    exportEnabled shouldBe true
                    meters.sslExpiry shouldBe true
                    meters.httpLatestLatency shouldBe true
                    meters.httpUptimeStatus shouldBe true
                    meters.sslStatus shouldBe true
                    meters.pushUptimeStatus shouldBe true
                    meters.icmpUptimeStatus shouldBe true
                    meters.icmpLatestLatency shouldBe true
                    meters.icmpLatestPacketLoss shouldBe true

                    with(exporters.prometheus) {
                        enabled shouldBe true
                        descriptions shouldBe true
                    }
                    with(exporters.openTelemetry) {
                        enabled shouldBe true
                        url shouldBe "http://otel-collector.example:4317"
                        step shouldBe "PT30M"
                    }
                }
                with(result.mcpServer) {
                    enabled shouldBe true
                }
                with(result.versionInfo) {
                    installedVersion shouldBe appGlobals.appVersion
                    latestVersion shouldBe "123.456.789"
                    latestVersionDetails.shouldNotBeNull().toString() shouldBe "https//something.com/details"
                    isUpToDate shouldBe false
                }
            }
        }
    }
}) {
    @MockBean(VersionChecker::class)
    fun versionChecker(): VersionChecker = mockk()
}

private const val TEST_OIDC_ISSUER = "http://localhost:59999/"
private const val TEST_OIDC_CLIENT_ID = "dummy-client-id"
private const val SETTINGS_API_KEY = "settingsApiKeysettingsApiKey"

@MicronautTest
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = SETTINGS_API_KEY)
@Property(name = "micronaut.security.oauth2.clients.oidc.enabled", value = "true")
@Property(name = "micronaut.security.oauth2.clients.oidc.client-id", value = TEST_OIDC_CLIENT_ID)
@Property(name = "micronaut.security.oauth2.clients.oidc.client-secret", value = "dummy-client-secret")
@Property(name = "micronaut.security.oauth2.clients.oidc.openid.issuer", value = TEST_OIDC_ISSUER)
class SettingsControllerOidcTest(
    @param:Client("/") private val client: HttpClient,
) : DatabaseBehaviorSpec({

    given("the SettingsController with OIDC enabled") {

        `when`("the getSettings method is called") {

            val request = HttpRequest.GET<Any>("/api/v2/settings").header("X-API-KEY", SETTINGS_API_KEY)
            val result = client.exchange(request, SettingsDto::class.java).awaitFirst().body().shouldNotBeNull()

            then("it should expose the OIDC provider settings") {
                result.authentication.enabled shouldBe true
                with(result.authentication.oidc.shouldNotBeNull()) {
                    issuer shouldBe TEST_OIDC_ISSUER
                    clientId shouldBe TEST_OIDC_CLIENT_ID
                }
            }
        }
    }
})
