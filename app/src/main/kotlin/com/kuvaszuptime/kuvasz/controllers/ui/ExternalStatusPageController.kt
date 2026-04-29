package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.security.Role
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import com.kuvaszuptime.kuvasz.ui.pages.statuspage.*
import com.kuvaszuptime.kuvasz.util.UIDefaults.STATUS_PAGE_PATH
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.exceptions.NotFoundException
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
    @Produces(MediaType.TEXT_HTML)
    fun getDefaultStatusPage(): String {
        // If the default status page is not public and the user is not authenticated, return 404
        if (!defaultStatusPageConfig.public && missesWebRole()) {
            throw NotFoundException()
        }
        return renderPublicStatusPage(
            appGlobals,
            pageData = statusPageDataActions.getCachedDefaultStatusPageData(),
        )
    }

    @Get("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    fun getStatusPage(@PathVariable slug: String): String {
        val existingPage = if (missesWebRole()) {
            statusPageActions.getStatusPageBySlug(slug, public = true)
        } else {
            statusPageActions.getStatusPageBySlug(slug)
        }
        val pageData = statusPageDataActions.getCachedStatusPageData(existingPage.id)

        return renderPublicStatusPage(
            appGlobals,
            pageData = pageData,
        )
    }

    private fun missesWebRole() = securityService?.hasRole(Role.WEB.alias) == false
}
