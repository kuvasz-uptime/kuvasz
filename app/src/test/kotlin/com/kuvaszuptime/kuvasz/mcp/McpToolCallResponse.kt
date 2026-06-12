package com.kuvaszuptime.kuvasz.mcp

/**
 * A convenience class to make extracting MCP errors with low-level clients easier
 */
data class McpToolCallResponse(
    val error: ProtocolError? = null,
) {
    data class ProtocolError(val code: Int, val message: String? = null)
}
