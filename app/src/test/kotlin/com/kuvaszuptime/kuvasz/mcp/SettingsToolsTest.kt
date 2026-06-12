package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.schemas.AppSettingsSchema
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.micronaut.context.annotation.Property
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient

@MicronautTest
@Property(name = "app-config.event-data-retention-days", value = "30")
@Property(name = "app-config.latency-data-retention-days", value = "14")
@Property(name = "app-config.language", value = "en")
@Property(name = "app-config.log-event-handler", value = "true")
@Property(name = "app-config.http-check-timeout-seconds", value = "15")
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
                        with(editabilityState) {
                            areHttpMonitorsReadOnly shouldBe false
                            arePushMonitorsReadOnly shouldBe false
                            areIcmpMonitorsReadOnly shouldBe false
                            areStatusPagesReadOnly shouldBe false
                        }
                    }

                    with(settings.versionInfo) {
                        installedVersion shouldBe appGlobals.appVersion
                        isUpToDate shouldBe true
                        latestVersion shouldBe null
                        latestVersionDetails shouldBe null
                    }

                    response.contentAs<AppSettingsSchema>() shouldBe settings
                }
            }
        }
    }
}
