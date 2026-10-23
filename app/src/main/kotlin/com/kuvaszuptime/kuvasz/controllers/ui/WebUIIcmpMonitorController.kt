package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.IcmpMonitorRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.icmp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.icmp.*
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
class WebUIIcmpMonitorController(
    private val monitorActions: IcmpMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: IcmpMonitorRepository,
) {

    @Get("/icmp-monitors")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitors(request: HttpRequest<*>) = renderIcmpMonitorsPage(
        globals = appGlobals,
        query = icmpMonitorListQuery(
            params = request.queryParams(),
            availableCategories = monitorRepository.fetchDistinctCategories().sortedBy { it.lowercase() },
        ),
    )

    @Get("/icmp-monitors/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderIcmpMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalUptimeStats(
                monitorType = MonitorType.ICMP,
                period = Duration.ofDays(UIDefaults.ICMP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/icmp-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitorList(request: HttpRequest<*>): HttpResponse<String> {
        val query = icmpMonitorListQuery(request.queryParams())
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
            renderIcmpMonitorList(monitors, shownQuery, appGlobals.editabilityState)
        }
    }

    @Get("/icmp-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun icmpMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderIcmpMonitorDetailsHeading(monitor))
            append(
                renderIcmpUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.ICMP,
                        period = Duration.ofDays(UIDefaults.ICMP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }
}
