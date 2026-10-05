package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_PROXIES
import com.kuvaszuptime.kuvasz.mcp.schemas.ProxyListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.ProxySchema
import com.kuvaszuptime.kuvasz.models.dto.proxy.ProxyType
import com.kuvaszuptime.kuvasz.testutils.PROXIES
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.client.McpSyncClient

@MicronautTest(environments = [PROXIES])
class ProxyToolsTest(
    @param:Client("/") private val client: HttpClient,
    mcpClient: McpSyncClient,
) : McpToolTest(client, mcpClient) {

    init {
        given("the list-proxies tool") {

            `when`("list-proxies is called with proxies configured") {
                val response = callToolWithMcpClient(LIST_PROXIES)

                then("it should return every configured proxy, sorted by name, without the credentials") {
                    response.isError shouldBe false

                    val proxyList = response.structuredContentAs<ProxyListSchema>().shouldNotBeNull()
                    proxyList.proxies shouldContainExactly listOf(
                        ProxySchema(
                            name = "corporate-egress",
                            type = ProxyType.HTTP,
                            host = "10.0.0.10",
                            port = 3128,
                            authenticated = true,
                        ),
                        ProxySchema(
                            name = "office-network",
                            type = ProxyType.SOCKS5,
                            host = "127.0.0.1",
                            port = 1080,
                            authenticated = false,
                        ),
                    )
                    response.contentAs<ProxyListSchema>() shouldBe proxyList
                    response.content.toString() shouldNotContain "s3cr3t"
                }
            }
        }
    }
}
