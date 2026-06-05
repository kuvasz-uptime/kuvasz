package com.kuvaszuptime.kuvasz.mcp

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode

@JsonIgnoreProperties(ignoreUnknown = true)
data class McpToolCallResponse(
    val result: ToolResult? = null,
    val error: ProtocolError? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ToolResult(
        val content: List<ContentBlock> = emptyList(),
        val isError: Boolean = false,
        val structuredContent: JsonNode? = null,
    ) {
        fun firstText(): String = content.firstOrNull { it.type == "text" }?.text.orEmpty()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ContentBlock(val type: String, val text: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ProtocolError(val code: Int, val message: String? = null)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class McpToolsListResponse(
    val result: ToolsResult? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ToolsResult(val tools: List<ToolInfo> = emptyList())

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ToolInfo(val name: String)
}

fun jsonRpcRequest(method: String): Map<String, Any> =
    mapOf("jsonrpc" to "2.0", "id" to 1, "method" to method)
