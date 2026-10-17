package com.kuvaszuptime.kuvasz.models.dto.pagination

import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class PagedResult<T>(
    @param:Schema(description = "The items on the requested page", required = true)
    val result: List<T>,
    @param:Schema(description = "The number of the requested page, starting from 1", required = true)
    val page: Int,
    @param:Schema(description = "The requested page size", required = true)
    val size: Int,
    @param:Schema(description = "The number of items matching the filters, on all the pages", required = true)
    val totalCount: Long,
    @param:Schema(description = "The number of pages, 0 if nothing matches the filters", required = true)
    val totalPages: Int,
) {

    /** The same page with its items replaced, e.g. by the enriched versions of them. */
    fun <R> withResult(result: List<R>): PagedResult<R> = PagedResult(result, page, size, totalCount, totalPages)

    companion object {
        fun <T> of(result: List<T>, pageRequest: PageRequest, totalCount: Long) = PagedResult(
            result = result,
            page = pageRequest.page,
            size = pageRequest.size,
            totalCount = totalCount,
            totalPages = ((totalCount + pageRequest.size - 1) / pageRequest.size).toInt(),
        )
    }
}
