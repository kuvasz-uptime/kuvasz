package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_PROXIES
import com.kuvaszuptime.kuvasz.mcp.schemas.ProxyListSchema
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient

@MicronautTest
class ProxyToolsWithoutProxiesTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-proxies tool") {

            `when`("list-proxies is called without any proxy configured") {
                val response = callToolWithMcpClient(LIST_PROXIES)

                then("it should return an empty list") {
                    response.isError shouldBe false
                    response.structuredContentAs<ProxyListSchema>().shouldNotBeNull().proxies.shouldBeEmpty()
                }
            }
        }
    }
}
