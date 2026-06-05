package com.kuvaszuptime.kuvasz.mcp

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.modelcontextprotocol.spec.McpSchema

abstract class KuvaszTools(private val objectMapper: ObjectMapper) {

    protected fun extractMonitorIdAndUpdates(request: McpSchema.CallToolRequest): Pair<Long, ObjectNode>? {
        val args = objectMapper.valueToTree<ObjectNode>(request.arguments() ?: return null)
        val monitorId = args["monitorId"]?.longValue() ?: return null
        val updates = args.deepCopy().apply { remove("monitorId") }
        return monitorId to updates
    }

    protected fun success(data: Any): McpSchema.CallToolResult =
        McpSchema.CallToolResult.builder()
            .addTextContent(objectMapper.writeValueAsString(data))
            .structuredContent(data)
            .isError(false)
            .build()

    protected fun error(message: String): McpSchema.CallToolResult =
        McpSchema.CallToolResult.builder()
            .addTextContent(message)
            .isError(true)
            .build()
}
