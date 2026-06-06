package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.MCP_PATH
import com.kuvaszuptime.kuvasz.util.getBodyAs
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import kotlinx.coroutines.reactive.awaitFirst

abstract class McpToolTest(
    private val client: HttpClient,
) : DatabaseBehaviorSpec() {

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
            try {
                e.response.getBodyAs<McpToolCallResponse>()!!
            } catch (_: Exception) {
                McpToolCallResponse(error = McpToolCallResponse.ProtocolError(e.status.code, e.message))
            }
        }
    }
}
