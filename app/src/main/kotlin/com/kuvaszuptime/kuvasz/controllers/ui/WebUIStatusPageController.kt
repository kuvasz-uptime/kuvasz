package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.pages.statuspage.*
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden

@Controller("/status-pages")
@Hidden
class WebUIStatusPageController(
    private val statusPageActions: StatusPageActions,
    private val statusPageDataActions: StatusPageDataActions,
    private val appGlobals: AppGlobals,
) {

    @Get("/")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun statusPages(request: HttpRequest<*>) =
        renderStatusPagesPage(appGlobals, statusPageListQuery(request.queryParams()))

    @Get("/{statusPageId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun statusPageDetails(@PathVariable statusPageId: Long): String {
        val statusPage = statusPageActions.getStatusPageById(statusPageId)
        val pageData = statusPageDataActions.getStatusPageData(statusPageId)

        return renderStatusPageDetailsPage(
            globals = appGlobals,
            statusPage = statusPage,
            pageData = pageData,
        )
    }

    @Get("/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun statusPageList(request: HttpRequest<*>): HttpResponse<String> {
        val query = statusPageListQuery(request.queryParams())
        val statusPages = fetchListPage(query.pageRequest) { pageRequest ->
            statusPageActions.getStatusPagesPage(
                public = query.filters.public.value,
                name = query.filters.name.value,
                sorting = query.sorting,
                pageRequest = pageRequest,
            )
        }
        return pagedListFragment(query, statusPages) { shownQuery ->
            renderStatusPageList(statusPages, shownQuery, appGlobals)
        }
    }
}
