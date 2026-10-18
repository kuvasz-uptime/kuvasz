package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageDetailsSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.StatusPageSchema
import com.kuvaszuptime.kuvasz.models.pagination.ListDefaults
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
        description = "Lists the status pages configured in Kuvasz with their basic configuration, sorted by their " +
            "title by default." + ListToolArgs.PAGINATION_NOTE,
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listStatusPages(
        @ToolArg(description = ListToolArgs.STATUS_PAGE_TITLE) name: String? = null,
        @ToolArg(description = ListToolArgs.PAGE) page: Int? = null,
        @ToolArg(description = ListToolArgs.SIZE) size: Int? = null,
        @ToolArg(description = ListToolArgs.SORTED_BY) sortedBy: String? = null,
        @ToolArg(description = ListToolArgs.SORT_ORDER) sortOrder: String? = null,
    ): StatusPageListSchema {
        val statusPages = statusPageActions.getStatusPagesPage(
            public = null,
            name = name,
            sorting = sortingOf(sortedBy, sortOrder, default = ListDefaults.STATUS_PAGE_SORTING),
            pageRequest = pageRequestOf(page, size),
        )
        return StatusPageListSchema(
            statusPages = statusPages.result.map { StatusPageSchema.fromDto(it) },
            page = statusPages.page,
            size = statusPages.size,
            totalCount = statusPages.totalCount,
            totalPages = statusPages.totalPages,
        )
    }

    @Tool(
        name = ToolNames.GET_STATUS_PAGE_DETAILS,
        description = "Get the full details of a specific status page by its ID, including per-monitor " +
            "uptime status, uptime ratio, and the daily uptime history of the last 'historyDays' days (30, or less " +
            "if the event data retention is shorter). Every day of the history carries its downtime, and the " +
            "incidents that were open on that day, with their public notes only.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun getStatusPageDetails(
        @ToolArg(description = "The numeric ID of the status page") statusPageId: Long,
    ): StatusPageDetailsSchema =
        StatusPageDetailsSchema.fromDto(statusPageDataActions.getStatusPageData(statusPageId))
}
