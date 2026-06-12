package com.kuvaszuptime.kuvasz.mcp

import tools.jackson.databind.JsonNode

data class McpToolCallResponse(
    val result: ToolResult? = null,
    val error: ProtocolError? = null,
) {
    data class ToolResult(
        val content: List<ContentBlock> = emptyList(),
        val isError: Boolean = false,
        val structuredContent: JsonNode? = null,
    ) {
        fun firstText(): String = content.firstOrNull { it.type == "text" }?.text.orEmpty()
    }

    data class ContentBlock(val type: String, val text: String? = null)

    data class ProtocolError(val code: Int, val message: String? = null)
}
