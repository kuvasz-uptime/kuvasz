package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.TcpMonitorRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.tcp.TcpMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.tcp.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.tcp.*
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
class WebUITcpMonitorController(
    private val monitorActions: TcpMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: TcpMonitorRepository,
) {

    @Get("/tcp-monitors")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitors(request: HttpRequest<*>) = renderTcpMonitorsPage(
        globals = appGlobals,
        query = tcpMonitorListQuery(
            params = request.queryParams(),
            availableCategories = monitorRepository.fetchDistinctCategories().sortedBy { it.lowercase() },
        ),
    )

    @Get("/tcp-monitors/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderTcpMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalUptimeStats(
                monitorType = MonitorType.TCP,
                period = Duration.ofDays(UIDefaults.TCP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/tcp-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitorList(request: HttpRequest<*>): HttpResponse<String> {
        val query = tcpMonitorListQuery(request.queryParams())
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
            renderTcpMonitorList(monitors, shownQuery, appGlobals.editabilityState, appGlobals.configuredProxies)
        }
    }

    @Get("/tcp-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun tcpMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderTcpMonitorDetailsHeading(monitor, appGlobals.configuredProxies))
            append(
                renderTcpUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.TCP,
                        period = Duration.ofDays(UIDefaults.TCP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }
}
