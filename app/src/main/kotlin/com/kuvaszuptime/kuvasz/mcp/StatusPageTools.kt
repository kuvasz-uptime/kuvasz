package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageSchema
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import jakarta.inject.Singleton

@Singleton
class StatusPageTools(
    private val statusPageActions: StatusPageActions,
    private val statusPageDataActions: StatusPageDataActions,
) {

    @Tool(
        name = ToolNames.LIST_STATUS_PAGES,
        description = "Lists all status pages configured in Kuvasz with their basic configuration",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listStatusPages(): StatusPageListSchema =
        StatusPageListSchema(
            statusPages = statusPageActions.getStatusPages(public = null).map { StatusPageSchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_STATUS_PAGE_DETAILS,
        description = "Get the full details of a specific status page by its ID, including per-monitor " +
            "uptime status, uptime ratio, and 30-day uptime history",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getStatusPageDetails(
        @ToolArg(description = "The numeric ID of the status page") statusPageId: Long,
    ): StatusPageDetailsSchema =
        StatusPageDetailsSchema.fromDto(statusPageDataActions.getStatusPageData(statusPageId))
}
