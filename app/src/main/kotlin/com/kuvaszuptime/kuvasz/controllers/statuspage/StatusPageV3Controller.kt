package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V3_PREFIX
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageSortField
import com.kuvaszuptime.kuvasz.models.dto.pagination.PagedResult
import com.kuvaszuptime.kuvasz.models.dto.pagination.SortOrder
import com.kuvaszuptime.kuvasz.models.pagination.PageRequest
import com.kuvaszuptime.kuvasz.models.pagination.Sorting
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("${API_V3_PREFIX}/status-pages", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.STATUS_PAGES)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class StatusPageV3Controller(
    private val actions: StatusPageActions,
) : StatusPageV3Operations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getStatusPagesPage(
        public: Boolean?,
        name: String?,
        page: Int,
        size: Int,
        sortedBy: StatusPageSortField?,
        sortOrder: SortOrder?,
    ): PagedResult<StatusPageDto> =
        actions.getStatusPagesPage(
            public = public,
            name = name,
            sorting = Sorting.of(sortedBy, sortOrder, default = DEFAULT_SORTING),
            pageRequest = PageRequest(page, size),
        )

    companion object {
        private val DEFAULT_SORTING = Sorting(StatusPageSortField.TITLE, SortOrder.ASC)
    }
}
