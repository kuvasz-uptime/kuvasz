package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.controllers.MCP_PATH
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_HTTP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_ICMP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.mcp.ToolNames.CREATE_PUSH_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_HTTP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_ICMP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.mcp.ToolNames.DELETE_PUSH_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_MAINTENANCE_WINDOW_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_HTTP_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_HTTP_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_ICMP_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_ICMP_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_PUSH_MONITOR_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_PUSH_MONITOR_STATS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_STATUS_PAGE_DETAILS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_HTTP_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_ICMP_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INCIDENTS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_MAINTENANCE_WINDOWS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_PUSH_MONITORS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_STATUS_PAGES
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_HTTP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_ICMP_MONITOR
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.mcp.ToolNames.TOGGLE_PUSH_MONITOR
import com.kuvaszuptime.kuvasz.security.TEST_API_KEY
import com.kuvaszuptime.kuvasz.security.TEST_MCP_API_KEY
import com.kuvaszuptime.kuvasz.security.TEST_PASSWORD
import com.kuvaszuptime.kuvasz.security.TEST_USERNAME
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.modelcontextprotocol.spec.McpSchema
import io.modelcontextprotocol.spec.McpSchema.METHOD_TOOLS_LIST
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

@MicronautTest(environments = ["yaml-monitors", "full-integrations-setup"])
@Property(name = "micronaut.security.enabled", value = "true")
@Property(name = "admin-auth.api-key", value = TEST_API_KEY)
@Property(name = "admin-auth.mcp-api-key", value = TEST_MCP_API_KEY)
@Property(name = "admin-auth.username", value = TEST_USERNAME)
@Property(name = "admin-auth.password", value = TEST_PASSWORD)
class McpServerAuthTest(
    @param:Client("/") private val client: HttpClient,
) : DatabaseBehaviorSpec() {

    private val objectMapper = jacksonObjectMapper()

    data class McpToolsListResponse(
        val result: ToolsResult? = null,
    ) {
        data class ToolsResult(val tools: List<ToolInfo> = emptyList())
        data class ToolInfo(val name: String)
    }

    fun toolsRequest() = HttpRequest.POST(
        MCP_PATH,
        McpSchema.JSONRPCRequest(McpSchema.JSONRPC_VERSION, METHOD_TOOLS_LIST, 1, emptyMap<String, Any>())
    )

    init {
        given("the MCP endpoint") {

            `when`("an unauthenticated request is made") {
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(toolsRequest(), String::class.java).awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a request is made with a wrong API key") {
                val request = toolsRequest().header("X-API-KEY", "wrongkey1234567890")
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, String::class.java).awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a request is made with an empty API key in the X-API-KEY header") {
                val request = toolsRequest().header("X-API-KEY", "")
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, String::class.java).awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a request is made with an empty API key in the Authorization header") {
                val request = toolsRequest().bearerAuth("")
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, String::class.java).awaitFirst()
                }
                then("it should return 401") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a request is made with the REST API key instead of the MCP API key") {
                val request = toolsRequest().header("X-API-KEY", TEST_API_KEY)
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, String::class.java).awaitFirst()
                }
                then("it should return 401, as the REST API key does not grant ROLE_MCP") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }

            `when`("a valid tools/list request is made") {
                val request = toolsRequest().header("X-API-KEY", TEST_MCP_API_KEY)
                val body = client.exchange(request, String::class.java).awaitFirst().body().shouldNotBeNull()
                val response = objectMapper.readValue<McpToolsListResponse>(body)

                then("it should return all registered MCP tools") {
                    val toolNames = response.result.shouldNotBeNull().tools.map { it.name }
                    toolNames shouldContainExactlyInAnyOrder listOf(
                        CREATE_HTTP_MONITOR,
                        CREATE_ICMP_MONITOR,
                        CREATE_PUSH_MONITOR,
                        GET_APP_SETTINGS,
                        GET_HTTP_MONITOR_DETAILS,
                        GET_HTTP_MONITOR_STATS,
                        GET_ICMP_MONITOR_DETAILS,
                        GET_ICMP_MONITOR_STATS,
                        GET_PUSH_MONITOR_DETAILS,
                        GET_PUSH_MONITOR_STATS,
                        GET_STATUS_PAGE_DETAILS,
                        LIST_HTTP_MONITORS,
                        LIST_ICMP_MONITORS,
                        LIST_INCIDENTS,
                        LIST_INTEGRATIONS,
                        LIST_PUSH_MONITORS,
                        LIST_STATUS_PAGES,
                        DELETE_HTTP_MONITOR,
                        DELETE_ICMP_MONITOR,
                        DELETE_PUSH_MONITOR,
                        TOGGLE_HTTP_MONITOR,
                        TOGGLE_ICMP_MONITOR,
                        TOGGLE_PUSH_MONITOR,
                        LIST_MAINTENANCE_WINDOWS,
                        GET_MAINTENANCE_WINDOW_DETAILS,
                        CREATE_MAINTENANCE_WINDOW,
                        TOGGLE_MAINTENANCE_WINDOW,
                        DELETE_MAINTENANCE_WINDOW,
                    )
                }
            }
        }

        given("the REST API endpoint") {

            `when`("a request is made with the MCP API key instead of the REST API key") {
                val request = HttpRequest.GET<Any>("$API_V2_PREFIX/http-monitors")
                    .header("X-API-KEY", TEST_MCP_API_KEY)
                val exception = shouldThrow<HttpClientResponseException> {
                    client.exchange(request, String::class.java).awaitFirst()
                }
                then("it should return 401, as the MCP API key does not grant ROLE_API") {
                    exception.status shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
