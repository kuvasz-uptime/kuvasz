package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.models.ui.ViewParams
import com.kuvaszuptime.kuvasz.models.ui.emptyViewParams
import com.kuvaszuptime.kuvasz.security.ui.WebAuthError
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
import com.kuvaszuptime.kuvasz.util.isHtmxRequest
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.views.ModelAndView
import io.micronaut.views.View
import io.micronaut.views.htmx.http.HtmxResponseHeaders
import io.swagger.v3.oas.annotations.Hidden

@Controller("/")
@Hidden
class WebUIController(
    private val monitorCrudService: MonitorCrudService
) {

    companion object {
        const val DASHBOARD_PATH = "/"
        const val LOGIN_PATH = "/login"
        const val MONITORS_PATH = "/monitors"
        const val MONITOR_TABLE_FRAGMENT_PATH = "/fragments/monitor-table"
        const val LOGIN_ERROR_MESSAGE = "Invalid username or password"
    }

    @View("dashboard")
    @Get(DASHBOARD_PATH)
    @WebSecured
    fun dashboard(): ViewParams = emptyViewParams()

    @View("monitors")
    @Get(MONITORS_PATH)
    @WebSecured
    fun monitors(): ViewParams = emptyViewParams()

    @View("login")
    @Get(LOGIN_PATH)
    fun login(@QueryValue error: Boolean?): ViewParams = emptyViewParams().apply {
        if (error == true) this["loginErrorMessage"] = LOGIN_ERROR_MESSAGE
    }

    @Get(MONITOR_TABLE_FRAGMENT_PATH)
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    fun monitorTable() = ModelAndView(
        "fragments/monitor-table",
        mutableMapOf(
            "monitors" to monitorCrudService.getMonitorsWithDetails(enabledOnly = false)
        )
    )

    /**
     * Handles authentication errors by redirecting to the login page
     */
    @Error
    @Suppress("UnusedParameter")
    fun authError(request: HttpRequest<*>, authError: WebAuthError): HttpResponse<*> =
        if (request.isHtmxRequest()) {
            // HTMX handles redirects differently, need to return a 2xx response with the right header
            HttpResponse.noContent<Any>().header(HtmxResponseHeaders.HX_REDIRECT, LOGIN_PATH)
        } else {
            HttpResponse.seeOther<Any>(LOGIN_PATH.toUri())
        }
}
