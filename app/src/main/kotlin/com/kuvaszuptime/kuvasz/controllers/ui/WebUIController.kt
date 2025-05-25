package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.jooq.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.models.ui.ViewParams
import com.kuvaszuptime.kuvasz.models.ui.emptyViewParams
import com.kuvaszuptime.kuvasz.security.ui.AlreadyLoggedInError
import com.kuvaszuptime.kuvasz.security.ui.UnauthorizedOnly
import com.kuvaszuptime.kuvasz.security.ui.WebAuthError
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.util.isHtmxRequest
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.views.ModelAndView
import io.micronaut.views.View
import io.micronaut.views.htmx.http.HtmxResponse
import io.micronaut.views.htmx.http.HtmxResponseHeaders
import io.swagger.v3.oas.annotations.Hidden

@Controller("/")
@Hidden
class WebUIController(
    private val monitorCrudService: MonitorCrudService,
    private val appConfig: AppConfig,
) {

    companion object {
        const val DASHBOARD_PATH = "/"
        const val LOGIN_PATH = "/login"
        const val LOGIN_ERROR_MESSAGE = "Invalid username or password"
        private const val MONITOR_KEY = "monitor"
        private const val SSL_EVENTS_COUNT = 5
        private const val UPTIME_EVENTS_COUNT = 5
    }

    @View("dashboard")
    @Get(DASHBOARD_PATH)
    @WebSecured
    fun dashboard(): ViewParams = emptyViewParams()

    @View("monitors")
    @Get("/monitors")
    @WebSecured
    fun monitors(): ViewParams = emptyViewParams()

    @View("monitor-details")
    @Get("/monitors/{monitorId}")
    @WebSecured
    fun monitorDetails(@PathVariable monitorId: Long): ViewParams {
        val monitor = monitorCrudService.getMonitorDetails(monitorId)

        return emptyViewParams().apply {
            put(MONITOR_KEY, monitor)
        }
    }

    @View("login")
    @Get(LOGIN_PATH)
    @UnauthorizedOnly
    fun login(@QueryValue error: Boolean?): ViewParams = emptyViewParams().apply {
        if (error == true) this["loginErrorMessage"] = LOGIN_ERROR_MESSAGE
    }

    @Get("/fragments/monitors/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    fun monitorTable(): String {
        val isReadOnlyMode = appConfig.isExternalWriteDisabled()
        val monitors = monitorCrudService.getMonitorsWithDetails(
            enabledOnly = false,
            sortedBy = MONITOR.NAME.asc()
        )

        return renderMonitorList(monitors, isReadOnlyMode)

//        return ModelAndView(
//            "fragments/monitor/monitors-list",
//            mutableMapOf(
//                "monitors" to monitorCrudService.getMonitorsWithDetails(
//                    enabledOnly = false,
//                    sortedBy = MONITOR.NAME.asc()
//                )
//            )
//        )
    }

    @Get("/fragments/monitors/{monitorId}/details-heading")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    fun monitorHeading(@PathVariable monitorId: Long): HtmxResponse<Any> {
        val monitor = monitorCrudService.getMonitorDetails(monitorId)
        return HtmxResponse.builder<Any>()
            .modelAndView(
                ModelAndView("fragments/monitor/details-heading", mutableMapOf(MONITOR_KEY to monitor))
            )
            .modelAndView(
                ModelAndView("fragments/monitor/details-uptime-summary", mutableMapOf(MONITOR_KEY to monitor))
            )
            .modelAndView(
                ModelAndView("fragments/monitor/details-ssl-summary", mutableMapOf(MONITOR_KEY to monitor))
            )
            .build()
    }

    @Get("/fragments/monitors/{monitorId}/details-uptime-events")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    fun monitorUptimeEvents(@PathVariable monitorId: Long) = ModelAndView(
        "fragments/monitor/details-uptime-events",
        mutableMapOf(
            "events" to monitorCrudService.getUptimeEventsByMonitorId(monitorId, UPTIME_EVENTS_COUNT)
        )
    )

    @Get("/fragments/monitors/{monitorId}/details-ssl-events")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    fun monitorSSLEvents(@PathVariable monitorId: Long) = ModelAndView(
        "fragments/monitor/details-ssl-events",
        mutableMapOf(
            "events" to monitorCrudService.getSSLEventsByMonitorId(monitorId, SSL_EVENTS_COUNT)
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

    @Error
    @Suppress("UnusedParameter")
    fun alreadyLoggedInError(request: HttpRequest<*>, authError: AlreadyLoggedInError): HttpResponse<*> =
        HttpResponse.seeOther<Any>(DASHBOARD_PATH.toUri())
}
