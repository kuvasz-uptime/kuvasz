package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.IcmpMonitor.ICMP_MONITOR
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.icmp.*
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
class WebUIIcmpMonitorController(
    private val monitorActions: IcmpMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: IcmpMonitorRepository,
    private val incidentRepository: IncidentRepository,
) {

    @Get("/icmp-monitors/fragments/stats")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitoringStats(): String {
        val period = Duration.ofDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS)

        return renderIcmpMonitoringStats(
            monitoringStats = statCalculator.calculateOverallIcmpStats(period),
            downMonitors = monitorActions.getMonitorsWithDetails(
                enabled = true,
                uptimeStatus = listOf(UptimeStatus.DOWN),
            ),
        )
    }

    @Get("/icmp-monitors")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitors() = renderIcmpMonitorsPage(appGlobals)

    @Get("/icmp-monitors/{monitorId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderIcmpMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalIcmpUptimeStats(
                period = Duration.ofDays(UIDefaults.ICMP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/icmp-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitorList(): String {
        val monitors = monitorActions.getMonitorsWithDetails(sortedBy = ICMP_MONITOR.NAME.asc())

        return renderIcmpMonitorList(monitors, appGlobals.editabilityState)
    }

    @Get("/icmp-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderIcmpMonitorDetailsHeading(monitor))
            append(
                renderIcmpUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalIcmpUptimeStats(
                        period = Duration.ofDays(UIDefaults.ICMP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }

    @Get("/icmp-monitors/fragments/details-uptime-incidents/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.IO)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitorUptimeIncidents(@PathVariable monitorId: Long) =
        monitorRepository.findById(monitorId, null)?.let { monitor ->
            renderIncidents(
                incidents = incidentRepository.getIcmpUptimeIncidents(
                    monitor.id,
                    period = Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS),
                    includeResolved = true,
                )
            )
        }
}
