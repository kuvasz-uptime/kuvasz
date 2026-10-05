package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_DOCKER_HOSTS
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerHostListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.DockerHostSchema
import com.kuvaszuptime.kuvasz.models.dto.docker.DockerHostAuthMethod
import com.kuvaszuptime.kuvasz.services.docker.client.DockerApiClient
import com.kuvaszuptime.kuvasz.testutils.DOCKER_HOSTS
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.mockk.every
import io.mockk.mockk
import io.modelcontextprotocol.client.McpSyncClient

@MicronautTest(environments = [DOCKER_HOSTS])
class DockerHostToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    // Neither host is a real daemon, so a negotiated version can only come from here
    @MockBean(DockerApiClient::class)
    fun apiClientMock(): DockerApiClient = mockk {
        every { negotiatedVersions() } returns mapOf("vps-1" to "1.44")
    }

    init {
        given("the list-docker-hosts tool") {

            `when`("list-docker-hosts is called with Docker hosts configured") {
                val response = callToolWithMcpClient(LIST_DOCKER_HOSTS)

                then("it should return every configured host, sorted by name, in both structured and text content") {
                    response.isError shouldBe false

                    val hostList = response.structuredContentAs<DockerHostListSchema>().shouldNotBeNull()
                    hostList.dockerHosts shouldContainExactly listOf(
                        DockerHostSchema(
                            name = "local",
                            url = "unix:///var/run/docker.sock",
                            tlsEnabled = false,
                            authMethod = DockerHostAuthMethod.UNIX_SOCKET,
                            apiVersion = null,
                        ),
                        DockerHostSchema(
                            name = "vps-1",
                            url = "tcp://10.0.0.5:2375",
                            tlsEnabled = false,
                            authMethod = DockerHostAuthMethod.NONE,
                            apiVersion = "1.44",
                        ),
                    )

                    response.contentAs<DockerHostListSchema>() shouldBe hostList
                }
            }
        }
    }
}
