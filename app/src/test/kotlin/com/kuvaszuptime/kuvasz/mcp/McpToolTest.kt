package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.MCP_PATH
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.modelcontextprotocol.client.McpSyncClient
import io.modelcontextprotocol.spec.McpSchema
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.module.kotlin.convertValue
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue

abstract class McpToolTest(
    private val client: HttpClient,
    private val mcpClient: McpSyncClient,
) : DatabaseBehaviorSpec() {

    protected val objectMapper = jacksonObjectMapper()

    /**
     * The errors on [io.modelcontextprotocol.client.McpClient] are everything but easy to extract, so it's more
     * convenient to use a low-level solution like this, because then we have the chance to
     * make assertions on the error responses in a straightforward way.
     */
    protected suspend fun callTool(
        toolName: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): McpToolCallResponse {
        val request = HttpRequest.POST(
            MCP_PATH,
            mapOf(
                "jsonrpc" to "2.0",
                "id" to 1,
                "method" to "tools/call",
                "params" to mapOf("name" to toolName, "arguments" to arguments)
            )
        )
        return try {
            client.retrieve(request, McpToolCallResponse::class.java).awaitFirst()
        } catch (e: HttpClientResponseException) {
            e.response.getBodyAs<McpToolCallResponse>().shouldNotBeNull()
        }
    }

    protected inline fun <reified T : Any> McpSchema.CallToolResult.structuredContentAs(): T? =
        try {
            objectMapper.convertValue<T>(this.structuredContent)
        } catch (_: Exception) {
            null
        }

    protected inline fun <reified T : Any> McpSchema.CallToolResult.contentAs(): T? =
        try {
            this.content.filterIsInstance<McpSchema.TextContent>().firstOrNull()?.let { textContent ->
                objectMapper.readValue<T>(textContent.text)
            }
        } catch (_: Exception) {
            null
        }

    protected fun callToolWithMcpClient(
        toolName: String,
        arguments: Map<String, Any?> = emptyMap(),
    ): McpSchema.CallToolResult = mcpClient.callTool(McpSchema.CallToolRequest(toolName, arguments))
}
