package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.DatabaseBehaviorSpec
import com.kuvaszuptime.kuvasz.controllers.MCP_PATH
import io.kotest.matchers.nulls.shouldNotBeNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import kotlinx.coroutines.reactive.awaitFirst
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

abstract class McpToolTest(
    private val client: HttpClient,
    private val objectMapper: ObjectMapper,
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
        val body = client.exchange(request, String::class.java).awaitFirst().body().shouldNotBeNull()
        return objectMapper.readValue(body)
    }
}
