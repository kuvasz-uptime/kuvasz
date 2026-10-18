package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.DnsMonitorRepository
import com.kuvaszuptime.kuvasz.repositories.DnsResolutionSnapshotRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.dns.DnsMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.dns.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.dns.*
import com.kuvaszuptime.kuvasz.util.UIDefaults
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
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
    private val snapshotRepository: DnsResolutionSnapshotRepository,
) {

    @Get("/dns-monitors")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitors(request: HttpRequest<*>) = renderDnsMonitorsPage(
        globals = appGlobals,
        query = dnsMonitorListQuery(
            params = request.queryParams(),
            availableCategories = monitorRepository.fetchDistinctCategories().sortedBy { it.lowercase() },
        ),
    )

    @Get("/dns-monitors/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderDnsMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalUptimeStats(
                monitorType = MonitorType.DNS,
                period = Duration.ofDays(UIDefaults.DNS_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/dns-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorList(request: HttpRequest<*>): HttpResponse<String> {
        val query = dnsMonitorListQuery(request.queryParams())
        val filters = query.filters
        val monitors = fetchListPage(query.pageRequest) { pageRequest ->
            monitorActions.getMonitorsPage(
                enabled = filters.enabled.value,
                uptimeStatus = filters.uptimeStatus.values,
                categoryFilter = filters.category.value,
                name = filters.name.value,
                sorting = query.sorting,
                pageRequest = pageRequest,
            )
        }
        return pagedListFragment(query, monitors) { shownQuery ->
            renderDnsMonitorList(monitors, shownQuery, appGlobals.editabilityState)
        }
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
                    stats = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DNS,
                        period = Duration.ofDays(UIDefaults.DNS_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }


    @Get("/dns-monitors/fragments/snapshot/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dnsMonitorSnapshot(@PathVariable monitorId: Long): String =
        renderDnsResolutionSnapshot(snapshotRepository.getSnapshot(monitorId))
}
