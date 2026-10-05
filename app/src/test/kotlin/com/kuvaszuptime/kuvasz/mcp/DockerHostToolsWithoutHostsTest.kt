package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_DOCKER_HOSTS
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerHostListSchema
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient

@MicronautTest
class DockerHostToolsWithoutHostsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-docker-hosts tool") {

            `when`("list-docker-hosts is called without any Docker host configured") {
                val response = callToolWithMcpClient(LIST_DOCKER_HOSTS)

                then("it should return an empty list rather than fail") {
                    response.isError shouldBe false
                    response.structuredContentAs<DockerHostListSchema>().shouldNotBeNull().dockerHosts.shouldBeEmpty()
                }
            }
        }
    }
}
