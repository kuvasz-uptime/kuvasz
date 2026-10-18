package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.HttpMonitorRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.http.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.http.*
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
class WebUIHttpMonitorController(
    private val monitorActions: HttpMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: HttpMonitorRepository,
) {

    @Get("/http-monitors")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitors(request: HttpRequest<*>) = renderHttpMonitorsPage(
        globals = appGlobals,
        query = httpMonitorListQuery(
            params = request.queryParams(),
            availableCategories = monitorRepository.fetchDistinctCategories().sortedBy { it.lowercase() },
        ),
    )

    @Get("/http-monitors/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderHttpMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalUptimeStats(
                monitorType = MonitorType.HTTP_SSL,
                period = Duration.ofDays(UIDefaults.HTTP_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/http-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorTable(request: HttpRequest<*>): HttpResponse<String> {
        val query = httpMonitorListQuery(request.queryParams())
        val filters = query.filters
        val monitors = fetchListPage(query.pageRequest) { pageRequest ->
            monitorActions.getMonitorsPage(
                enabled = filters.monitor.enabled.value,
                uptimeStatus = filters.monitor.uptimeStatus.values,
                sslStatus = filters.sslStatus.values,
                sslCheckEnabled = filters.sslCheckEnabled.value,
                categoryFilter = filters.monitor.category.value,
                name = filters.monitor.name.value,
                sorting = query.sorting,
                pageRequest = pageRequest,
            )
        }
        return pagedListFragment(query, monitors) { shownQuery ->
            renderHttpMonitorList(monitors, shownQuery, appGlobals.editabilityState, appGlobals.configuredProxies)
        }
    }

    @Get("/http-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun httpMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderHttpMonitorDetailsHeading(monitor, appGlobals.configuredProxies))
            append(
                renderHttpUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.HTTP_SSL,
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
}
