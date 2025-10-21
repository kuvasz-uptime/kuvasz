package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.HttpMonitor.HTTP_MONITOR
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.http.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
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
    private val monitorActions: HttpMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: HttpMonitorRepository,
    private val incidentRepository: IncidentRepository,
) {

    @Get("/http-monitors/fragments/stats")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitoringStats(): String {
        val period = Duration.ofDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS)

        return renderHttpMonitoringStats(
            monitoringStats = statCalculator.calculateOverallHttpStats(period),
            downMonitors = monitorActions.getMonitorsWithDetails(
                enabled = true,
                uptimeStatus = listOf(UptimeStatus.DOWN),
            ),
            problematicSslMonitors = monitorActions.getMonitorsWithDetails(
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
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderHttpMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalHttpUptimeStats(
                period = Duration.ofDays(UIDefaults.HTTP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/http-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorTable(): String {
        val monitors = monitorActions.getMonitorsWithDetails(sortedBy = HTTP_MONITOR.NAME.asc())

        return renderHttpMonitorList(monitors, appGlobals.editabilityState)
    }

    @Get("/http-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderHttpMonitorDetailsHeading(monitor))
            append(
                renderHttpUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalHttpUptimeStats(
                        period = Duration.ofDays(UIDefaults.HTTP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
            if (monitor.sslCheckEnabled) {
                append(renderSSLSummary(monitor))
            }
        }
    }

    @Get("/http-monitors/fragments/details-uptime-incidents/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorUptimeIncidents(@PathVariable monitorId: Long) =
        monitorRepository.findById(monitorId, null)?.let { monitor ->
            renderIncidents(
                incidents = incidentRepository.getHttpUptimeIncidents(
                    monitor.id,
                    period = Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS),
                    includeResolved = true,
                )
            )
        }

    @Get("/http-monitors/fragments/details-ssl-incidents/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorSSLIncidents(@PathVariable monitorId: Long) =
        monitorRepository.findById(monitorId, null)?.let { monitor ->
            renderIncidents(
                incidents = incidentRepository.getSslIncidents(
                    monitor.id,
                    period = Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS),
                    includeResolved = true,
                )
            )
        }
}
