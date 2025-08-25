package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorCrudService
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.pages.*
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.swagger.v3.oas.annotations.Hidden
import java.time.Duration

@Controller("/")
@Hidden
class WebUIHttpMonitorController(
    private val monitorCrudService: HttpMonitorCrudService,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
) {

    companion object {
        private const val SSL_EVENTS_COUNT = 5
        private const val UPTIME_EVENTS_COUNT = 5
        private const val DASHBOARD_STATS_PERIOD_DEFAULT_DAYS = 7L
    }

    @Get("/http-monitors/fragments/stats")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitoringStats(): String {
        val period = Duration.ofDays(DASHBOARD_STATS_PERIOD_DEFAULT_DAYS)

        return renderMonitoringStats(
            monitoringStats = statCalculator.calculateOverallHttpStats(period),
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

    @Get("/http-monitors")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitors() = renderHttpMonitorsPage(appGlobals)

    @Get("/http-monitors/{monitorId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorCrudService.getMonitorDetails(monitorId)

        return renderHttpMonitorDetailsPage(appGlobals, monitor)
    }

    @Get("/http-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorTable(): String {
        val monitors = monitorCrudService.getMonitorsWithDetails(sortedBy = HTTP_MONITOR.NAME.asc())

        return renderHttpMonitorList(monitors, appGlobals.editabilityState.areHttpMonitorsReadOnly())
    }

    @Get("/http-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorCrudService.getMonitorDetails(monitorId)
        return buildString {
            append(renderHttpMonitorDetailsHeading(monitor))
            append(renderUptimeSummary(monitor))
            if (monitor.sslCheckEnabled) {
                append(renderSSLSummary(monitor))
            }
        }
    }

    @Get("/http-monitors/fragments/details-uptime-events/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorUptimeEvents(@PathVariable monitorId: Long): String =
        renderHttpUptimeEvents(
            events = monitorCrudService.getUptimeEventsByMonitorId(monitorId, UPTIME_EVENTS_COUNT)
        )

    @Get("/http-monitors/fragments/details-ssl-events/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorSSLEvents(@PathVariable monitorId: Long) =
        renderSSLEvents(
            events = monitorCrudService.getSSLEventsByMonitorId(monitorId, SSL_EVENTS_COUNT)
        )
}
