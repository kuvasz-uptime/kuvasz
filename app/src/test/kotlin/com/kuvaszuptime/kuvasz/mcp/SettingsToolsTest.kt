package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.schemas.AppSettingsSchema
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityState
import com.kuvaszuptime.kuvasz.models.settings.ConnectivityStatus
import com.kuvaszuptime.kuvasz.services.connectivity.ConnectivityChecker
import com.kuvaszuptime.kuvasz.testutils.ENABLED_CONNECTIVITY_CHECK
import com.kuvaszuptime.kuvasz.util.getCurrentTimestamp
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.MicronautKotest5Extension.getMock
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.client.McpSyncClient
import java.time.OffsetDateTime

@MicronautTest
@Property(name = "app-config.event-data-retention-days", value = "30")
@Property(name = "app-config.latency-data-retention-days", value = "14")
@Property(name = "app-config.language", value = "en")
@Property(name = "app-config.log-event-handler", value = "true")
@Property(name = "app-config.http-check-timeout-seconds", value = "15")
@Property(name = "app-config.http-check-max-redirects", value = "3")
class SettingsToolsTest(
    @param:Client("/") private val client: HttpClient,
    private val appGlobals: AppGlobals,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the get-app-settings tool") {

            `when`("get-app-settings is called") {
                val response = callToolWithMcpClient(GET_APP_SETTINGS)

                then("it should populate all AppSettingsSchema fields in both structured and text content") {
                    response.isError shouldBe false

                    val settings = response.structuredContentAs<AppSettingsSchema>().shouldNotBeNull()

                    with(settings.app) {
                        version.shouldNotBeEmpty() shouldBe appGlobals.appVersion
                        eventDataRetentionDays shouldBe 30
                        latencyDataRetentionDays shouldBe 14
                        language shouldBe "en"
                        eventLoggingEnabled shouldBe true
                        updateChecksEnabled shouldBe false
                        httpCheckTimeoutSeconds shouldBe 15L
                        httpCheckMaxRedirects shouldBe 3
                        with(editabilityState) {
                            areHttpMonitorsReadOnly shouldBe false
                            arePushMonitorsReadOnly shouldBe false
                            areIcmpMonitorsReadOnly shouldBe false
                            areTcpMonitorsReadOnly shouldBe false
                            areDnsMonitorsReadOnly shouldBe false
                            areStatusPagesReadOnly shouldBe false
                            areMaintenanceWindowsReadOnly shouldBe false
                        }
                    }

                    with(settings.versionInfo) {
                        installedVersion shouldBe appGlobals.appVersion
                        isUpToDate shouldBe true
                        latestVersion shouldBe null
                        latestVersionDetails shouldBe null
                    }

                    // The connectivity check is disabled by default, so it must not be exposed at all
                    settings.connectivityCheck.shouldBeNull()

                    response.contentAs<AppSettingsSchema>() shouldBe settings
                }
            }
        }
    }
}

@MicronautTest(environments = [ENABLED_CONNECTIVITY_CHECK])
class SettingsToolsConnectivityCheckTest(
    @param:Client("/") private val client: HttpClient,
    private val connectivityChecker: ConnectivityChecker,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        fun status(state: ConnectivityState, downSince: OffsetDateTime?, lastError: String?) = ConnectivityStatus(
            state = state,
            targets = listOf("1.1.1.1:53", "8.8.8.8:53"),
            intervalSeconds = 3600,
            timeoutSeconds = 5,
            lastCheckedAt = getCurrentTimestamp(),
            lastSuccessfulCheckAt = null,
            downSince = downSince,
            lastError = lastError,
        )

        given("the get-app-settings tool with an enabled connectivity check") {

            `when`("the connectivity is lost") {
                val downSince = getCurrentTimestamp()
                every { getMock(connectivityChecker).getStatus() } returns
                    status(ConnectivityState.DOWN, downSince, "1.1.1.1:53 (Connection refused)")

                val response = callToolWithMcpClient(GET_APP_SETTINGS)

                then("it should populate every ConnectivityCheckSchema field in both structured and text content") {
                    response.isError shouldBe false

                    val settings = response.structuredContentAs<AppSettingsSchema>().shouldNotBeNull()

                    with(settings.connectivityCheck.shouldNotBeNull()) {
                        state shouldBe ConnectivityState.DOWN.name
                        checksSuspended shouldBe true
                        targets shouldBe listOf("1.1.1.1:53", "8.8.8.8:53")
                        intervalSeconds shouldBe 3600L
                        timeoutSeconds shouldBe 5L
                        this.downSince shouldBe downSince.toString()
                        lastError shouldBe "1.1.1.1:53 (Connection refused)"
                    }

                    response.contentAs<AppSettingsSchema>() shouldBe settings
                }
            }

            `when`("the connectivity is up") {
                every { getMock(connectivityChecker).getStatus() } returns
                    status(ConnectivityState.UP, downSince = null, lastError = null)

                val response = callToolWithMcpClient(GET_APP_SETTINGS)

                then("the optional fields should be omitted instead of being reported as null") {
                    response.isError shouldBe false

                    val settings = response.structuredContentAs<AppSettingsSchema>().shouldNotBeNull()

                    with(settings.connectivityCheck.shouldNotBeNull()) {
                        state shouldBe ConnectivityState.UP.name
                        checksSuspended shouldBe false
                        downSince.shouldBeNull()
                        lastError.shouldBeNull()
                    }

                    @Suppress("UNCHECKED_CAST")
                    val rawSchema = response.structuredContentAs<Map<String, Any?>>()
                        .shouldNotBeNull()["connectivityCheck"] as Map<String, Any?>
                    rawSchema.keys shouldBe setOf(
                        "state",
                        "checksSuspended",
                        "targets",
                        "intervalSeconds",
                        "timeoutSeconds",
                    )
                }
            }
        }
    }

    @MockBean(ConnectivityChecker::class)
    fun connectivityChecker(): ConnectivityChecker = mockk()
}
