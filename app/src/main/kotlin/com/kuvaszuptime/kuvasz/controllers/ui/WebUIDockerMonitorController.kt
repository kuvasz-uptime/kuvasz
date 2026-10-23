package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.repositories.DockerMonitorRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.docker.DockerMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.monitor.docker.*
import com.kuvaszuptime.kuvasz.ui.pages.monitor.docker.*
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
class WebUIDockerMonitorController(
    private val monitorActions: DockerMonitorActions,
    private val appGlobals: AppGlobals,
    private val statCalculator: StatCalculator,
    private val monitorRepository: DockerMonitorRepository,
) {

    @Get("/docker-monitors")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dockerMonitors(request: HttpRequest<*>) = renderDockerMonitorsPage(
        globals = appGlobals,
        query = dockerMonitorListQuery(
            params = request.queryParams(),
            availableCategories = monitorRepository.fetchDistinctCategories().sortedBy { it.lowercase() },
        ),
    )

    @Get("/docker-monitors/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dockerMonitorDetails(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)

        return renderDockerMonitorDetailsPage(
            appGlobals,
            monitor,
            stats = statCalculator.calculateHistoricalUptimeStats(
                monitorType = MonitorType.DOCKER,
                period = Duration.ofDays(UIDefaults.DOCKER_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                monitorId = monitor.id,
            ),
        )
    }

    @Get("/docker-monitors/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dockerMonitorList(request: HttpRequest<*>): HttpResponse<String> {
        val query = dockerMonitorListQuery(request.queryParams())
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
            renderDockerMonitorList(monitors, shownQuery, appGlobals.editabilityState, appGlobals.configuredDockerHosts)
        }
    }

    @Get("/docker-monitors/fragments/details-heading/{monitorId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun dockerMonitorHeading(@PathVariable monitorId: Long): String {
        val monitor = monitorActions.getMonitorDetails(monitorId)
        return buildString {
            append(renderDockerMonitorDetailsHeading(monitor, appGlobals.configuredDockerHosts))
            append(
                renderDockerUptimeSummary(
                    monitor = monitor,
                    stats = statCalculator.calculateHistoricalUptimeStats(
                        monitorType = MonitorType.DOCKER,
                        period = Duration.ofDays(UIDefaults.DOCKER_MONITOR_UPTIME_STATS_PERIOD_DAYS),
                        monitorId = monitor.id,
                    )
                )
            )
        }
    }
}
