package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.tables.StatusPage.STATUS_PAGE
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import com.kuvaszuptime.kuvasz.ui.fragments.statuspage.*
import com.kuvaszuptime.kuvasz.ui.pages.statuspage.*
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
    fun statusPages() = renderStatusPagesPage(appGlobals)

    @Get("/{statusPageId}")
    @WebSecured
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
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun statusPageList(): String {
        val statusPages = statusPageActions.getStatusPages(public = null, sortedBy = STATUS_PAGE.TITLE.asc())

        return renderStatusPageList(
            statusPages = statusPages,
            appGlobals = appGlobals,
        )
    }
}
