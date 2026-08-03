package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.jooq.tables.DnsMonitor.DNS_MONITOR
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.dashboard.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.*
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.dns.*
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
class WebUIDnsMonitorController(
    private val monitorActions: DnsMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: DnsMonitorRepository,
    private val incidentRepository: IncidentRepository,
    private val snapshotRepository: DnsResolutionSnapshotRepository,
) {

    @Get("/dns-monitors/fragments/stats")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitoringStats(): String {
        val period = Duration.ofDays(UIDefaults.DASHBOARD_MONITORING_STATS_PERIOD_DAYS)

        return renderDnsMonitoringStats(
            monitoringStats = statCalculator.calculateOverallDnsStats(period),
            downMonitors = monitorActions.getMonitorsWithDetails(
                enabled = true,
                uptimeStatus = listOf(UptimeStatus.DOWN),
            ),
        )
    }

    @Get("/dns-monitors")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitors() = renderDnsMonitorsPage(appGlobals)

    @Get("/dns-monitors/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderDnsMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalDnsUptimeStats(
                period = Duration.ofDays(UIDefaults.DNS_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/dns-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorList(): String {
        val monitors = monitorActions.getMonitorsWithDetails(sortedBy = DNS_MONITOR.NAME.asc())

        return renderDnsMonitorList(monitors, appGlobals.editabilityState)
    }

    @Get("/dns-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderDnsMonitorDetailsHeading(monitor))
            append(
                renderDnsUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalDnsUptimeStats(
                        period = Duration.ofDays(UIDefaults.DNS_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }

    @Get("/dns-monitors/fragments/details-uptime-incidents/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorUptimeIncidents(@PathVariable monitorId: Long) =
        monitorRepository.findById(monitorId, null)?.let { monitor ->
            renderIncidents(
                incidents = incidentRepository.getDnsUptimeIncidents(
                    monitor.id,
                    period = Duration.ofDays(UIDefaults.INCIDENTS_PERIOD_DAYS),
                    includeResolved = true,
                )
            )
        }

    @Get("/dns-monitors/fragments/snapshot/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorSnapshot(@PathVariable monitorId: Long): String =
        renderDnsResolutionSnapshot(snapshotRepository.getSnapshot(monitorId))
}
