package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDataDto
import com.kuvaszuptime.kuvasz.security.Role
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import com.kuvaszuptime.kuvasz.ui.pages.statuspage.*
import com.kuvaszuptime.kuvasz.util.UIDefaults.STATUS_PAGE_PATH
import io.micronaut.core.util.StringUtils
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.NotFoundException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.utils.SecurityService
import io.swagger.v3.oas.annotations.Hidden

@Controller(STATUS_PAGE_PATH)
@Hidden
class ExternalStatusPageController(
    private val statusPageActions: StatusPageActions,
    private val statusPageDataActions: StatusPageDataActions,
    private val appGlobals: AppGlobals,
    private val defaultStatusPageConfig: DefaultStatusPageConfig,
    private val securityService: SecurityService?,
) {

    @Get("/")
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun getDefaultStatusPage(@Header(HX_REQUEST_HEADER) hxRequest: String?): HttpResponse<String> {
        // If the default status page is not public and the user is not authenticated, return 404
        if (!defaultStatusPageConfig.public && missesWebRole()) {
            throw NotFoundException()
        }
        return render(statusPageDataActions.getCachedDefaultStatusPageData(), hxRequest)
    }

    @Get("/{slug}")
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun getStatusPage(@PathVariable slug: String, @Header(HX_REQUEST_HEADER) hxRequest: String?): HttpResponse<String> {
        val existingPage = if (missesWebRole()) {
            statusPageActions.getStatusPageBySlug(slug, public = true)
        } else {
            statusPageActions.getStatusPageBySlug(slug)
        }
        return render(statusPageDataActions.getCachedStatusPageData(existingPage.id), hxRequest)
    }

    /**
     * The whole page, or only the part of it that refreshes itself when the page requests it (via htmx). The page
     * refreshes itself through its own URL, so the two responses have to be told apart by every cache too.
     */
    private fun render(pageData: StatusPageDataDto, hxRequest: String?): HttpResponse<String> {
        val body = if (hxRequest == StringUtils.TRUE) {
            renderPublicStatusPageContent(pageData)
        } else {
            renderPublicStatusPage(appGlobals, pageData)
        }
        return HttpResponse.ok(body).header(HttpHeaders.VARY, HX_REQUEST_HEADER)
    }

    private fun missesWebRole() = securityService?.hasRole(Role.WEB.alias) == false

    private companion object {
        const val HX_REQUEST_HEADER = "HX-Request"
    }
}
