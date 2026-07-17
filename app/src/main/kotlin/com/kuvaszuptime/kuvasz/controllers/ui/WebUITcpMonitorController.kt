package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.TcpMonitor.TCP_MONITOR
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.tcp.*
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
class WebUITcpMonitorController(
    private val monitorActions: TcpMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: TcpMonitorRepository,
    private val incidentRepository: IncidentRepository,
) {

    @Get("/tcp-monitors/fragments/stats")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitoringStats(): String {
        val period = Duration.ofDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS)

        return renderTcpMonitoringStats(
            monitoringStats = statCalculator.calculateOverallTcpStats(period),
            downMonitors = monitorActions.getMonitorsWithDetails(
                enabled = true,
                uptimeStatus = listOf(UptimeStatus.DOWN),
            ),
        )
    }

    @Get("/tcp-monitors")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitors() = renderTcpMonitorsPage(appGlobals)

    @Get("/tcp-monitors/{monitorId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderTcpMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalTcpUptimeStats(
                period = Duration.ofDays(UIDefaults.TCP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/tcp-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitorList(): String {
        val monitors = monitorActions.getMonitorsWithDetails(sortedBy = TCP_MONITOR.NAME.asc())

        return renderTcpMonitorList(monitors, appGlobals.editabilityState)
    }

    @Get("/tcp-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderTcpMonitorDetailsHeading(monitor))
            append(
                renderTcpUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalTcpUptimeStats(
                        period = Duration.ofDays(UIDefaults.TCP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }

    @Get("/tcp-monitors/fragments/details-uptime-incidents/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitorUptimeIncidents(@PathVariable monitorId: Long) =
        monitorRepository.findById(monitorId, null)?.let { monitor ->
            renderIncidents(
                incidents = incidentRepository.getTcpUptimeIncidents(
                    monitor.id,
                    period = Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS),
                    includeResolved = true,
                )
            )
        }
}
