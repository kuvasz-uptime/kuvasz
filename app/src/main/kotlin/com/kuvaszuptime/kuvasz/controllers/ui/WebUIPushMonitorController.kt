package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.PushMonitor.PUSH_MONITOR
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.repositories.PushMonitorRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.push.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.push.*
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
class WebUIPushMonitorController(
    private val monitorActions: PushMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: PushMonitorRepository,
    private val incidentRepository: IncidentRepository,
) {

    @Get("/push-monitors/fragments/stats")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun pushMonitoringStats(): String {
        val period = Duration.ofDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS)

        return renderPushMonitoringStats(
            monitoringStats = statCalculator.calculateOverallPushStats(period),
            downMonitors = monitorActions.getMonitorsWithDetails(
                enabled = true,
                uptimeStatus = listOf(UptimeStatus.DOWN),
            ),
        )
    }

    @Get("/push-monitors")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun pushMonitors() = renderPushMonitorsPage(appGlobals)

    @Get("/push-monitors/{monitorId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun pushMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderPushMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalPushUptimeStats(
                period = Duration.ofDays(UIDefaults.PUSH_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/push-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun pushMonitorList(): String {
        val monitors = monitorActions.getMonitorsWithDetails(sortedBy = PUSH_MONITOR.NAME.asc())

        return renderPushMonitorList(monitors, appGlobals.editabilityState)
    }

    @Get("/push-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun pushMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderPushMonitorDetailsHeading(monitor))
            append(
                renderPushUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalPushUptimeStats(
                        period = Duration.ofDays(UIDefaults.PUSH_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }

    @Get("/push-monitors/fragments/details-uptime-incidents/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun pushMonitorUptimeIncidents(@PathVariable monitorId: Long) =
        monitorRepository.findById(monitorId, null)?.let { monitor ->
            renderIncidents(
                incidents = incidentRepository.getPushUptimeIncidents(
                    monitor.id,
                    period = Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS),
                    includeResolved = true,
                )
            )
        }
}
