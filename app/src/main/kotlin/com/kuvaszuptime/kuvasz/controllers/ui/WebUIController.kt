package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.i18n.Messages
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.Monitor.MONITOR
import com.kuvaszuptime.kuvasz.repositories.SettingsRepository
import com.kuvaszuptime.kuvasz.security.ui.AlreadyLoggedInError
import com.kuvaszuptime.kuvasz.security.ui.UnauthorizedOnly
import com.kuvaszuptime.kuvasz.security.ui.WebAuthError
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import com.kuvaszuptime.kuvasz.util.isHtmxRequest
import com.kuvaszuptime.kuvasz.util.toUri
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.views.htmx.http.HtmxResponseHeaders
import io.swagger.v3.oas.annotations.Hidden
import java.time.Duration

@Controller("/")
@Hidden
class WebUIController(
    private val monitorCrudService: MonitorCrudService,
    private val appConfig: AppConfig,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val settingsRepository: SettingsRepository,
) {

    companion object {
        const val DASHBOARD_PATH = "/"
        const val LOGIN_PATH = "/login"
        private const val SSL_EVENTS_COUNT = 5
        private const val UPTIME_EVENTS_COUNT = 5
        private const val DASHBOARD_STATS_PERIOD_DEFAULT_DAYS = 7L
    }

    @Get(DASHBOARD_PATH)
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.IO)
    fun dashboard() = renderDashboard(appGlobals)

    @Get("/fragments/monitors/stats")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun monitoringStats(): String {
        val period = Duration.ofDays(DASHBOARD_STATS_PERIOD_DEFAULT_DAYS)

        return renderMonitoringStats(
            monitoringStats = statCalculator.calculateOverallStats(period),
            downMonitors = monitorCrudService.getMonitorsWithDetails(
                enabled = true,
                uptimeStatus = listOf(UptimeStatus.DOWN),
            ),
            problematicSslMonitors = monitorCrudService.getMonitorsWithDetails(
                enabled = true,
                sslCheckEnabled = true,
                sslStatus = listOf(SslStatus.INVALID, SslStatus.WILL_EXPIRE),
            )
        )
    }

    @Get("/monitors")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun monitors() = renderMonitorsPage(appGlobals)

    @Get("/monitors/{monitorId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun monitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorCrudService.getMonitorDetails(monitorId)

        return renderMonitorDetailsPage(appGlobals, monitor)
    }

    @Get(LOGIN_PATH)
    @UnauthorizedOnly
    @Produces(MediaType.TEXT_HTML)
    fun login(@QueryValue error: Boolean?): String = renderLoginPage(
        appGlobals,
        loginErrorMessage = if (error == true) Messages.invalidCredentials() else null,
    )

    @Get("/fragments/monitors/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun monitorTable(): String {
        val isReadOnlyMode = appConfig.isExternalWriteDisabled()
        val monitors = monitorCrudService.getMonitorsWithDetails(sortedBy = MONITOR.NAME.asc())

        return renderMonitorList(monitors, isReadOnlyMode)
    }

    @Get("/fragments/monitors/{monitorId}/details-heading")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun monitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorCrudService.getMonitorDetails(monitorId)
        return buildString {
            append(renderMonitorDetailsHeading(monitor))
            append(renderUptimeSummary(monitor))
            append(renderSSLSummary(monitor))
        }
    }

    @Get("/fragments/monitors/{monitorId}/details-uptime-events")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun monitorUptimeEvents(@PathVariable monitorId: Long): String =
        renderUptimeEvents(
            events = monitorCrudService.getUptimeEventsByMonitorId(monitorId, UPTIME_EVENTS_COUNT)
        )

    @Get("/fragments/monitors/{monitorId}/details-ssl-events")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun monitorSSLEvents(@PathVariable monitorId: Long) =
        renderSSLEvents(
            events = monitorCrudService.getSSLEventsByMonitorId(monitorId, SSL_EVENTS_COUNT)
        )

    @Get("/settings")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    @ExecuteOn(TaskExecutors.IO)
    fun settings() = renderSettings(appGlobals, settingsRepository.getSettings())

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
