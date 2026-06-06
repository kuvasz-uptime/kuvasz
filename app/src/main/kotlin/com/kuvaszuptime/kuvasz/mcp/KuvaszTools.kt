package com.kuvaszuptime.kuvasz.mcp

import io.modelcontextprotocol.spec.McpSchema
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

abstract class KuvaszTools(private val objectMapper: ObjectMapper) {

    protected fun extractMonitorIdAndUpdates(request: McpSchema.CallToolRequest): Pair<Long, ObjectNode>? =
        objectMapper.valueToTree<ObjectNode>(request.arguments())?.let { args ->
            val monitorId = args["monitorId"]?.longValue() ?: return@let null
            val updates = args.deepCopy().apply { remove("monitorId") }
            monitorId to updates
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
