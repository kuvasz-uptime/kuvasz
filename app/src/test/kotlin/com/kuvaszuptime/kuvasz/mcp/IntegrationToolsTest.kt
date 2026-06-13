package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.schemas.IntegrationListSchema
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import io.kotest.inspectors.forOne
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient

@MicronautTest(environments = ["full-integrations-setup"])
class IntegrationToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-integrations tool") {

            `when`("list-integrations is called with integrations configured") {
                val response = callToolWithMcpClient(LIST_INTEGRATIONS)

                then("it should return all configured integrations in both structured and text content") {
                    response.isError shouldBe false

                    val integrationList = response.structuredContentAs<IntegrationListSchema>().shouldNotBeNull()
                    integrationList.integrations.shouldHaveSize(21)

                    // Checking only one since the mapping should be the same for all of them
                    integrationList.integrations.forOne { integration ->
                        integration.id shouldBe "slack:test_implicitly_enabled"
                        integration.enabled shouldBe true
                        integration.global shouldBe false
                        integration.type shouldBe IntegrationType.SLACK
                    }

                    response.contentAs<IntegrationListSchema>() shouldBe integrationList
                }
            }
        }
    }
}
