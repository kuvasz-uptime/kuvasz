package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.repositories.SharedMonitorRepository
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowActions
import com.kuvaszuptime.kuvasz.ui.fragments.maintenance.*
import com.kuvaszuptime.kuvasz.ui.pages.maintenance.*
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

@Controller("/maintenance-windows")
@Hidden
class WebUIMaintenanceWindowController(
    private val maintenanceWindowActions: MaintenanceWindowActions,
    private val sharedMonitorRepository: SharedMonitorRepository,
    private val appGlobals: AppGlobals,
) {

    @Get("/")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun maintenanceWindows(request: HttpRequest<*>) =
        renderMaintenanceWindowsPage(appGlobals, maintenanceWindowListQuery(request.queryParams()))

    @Get("/{maintenanceWindowId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun maintenanceWindowDetails(@PathVariable maintenanceWindowId: Long): String {
        val maintenanceWindow = maintenanceWindowActions.getMaintenanceWindowById(maintenanceWindowId)

        return renderMaintenanceWindowDetailsPage(
            globals = appGlobals,
            maintenanceWindow = maintenanceWindow,
            monitorIds = sharedMonitorRepository.fetchNumericIds(maintenanceWindow.monitors),
        )
    }

    @Get("/fragments/details-heading/{maintenanceWindowId}")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun maintenanceWindowHeading(@PathVariable maintenanceWindowId: Long): String {
        val maintenanceWindow = maintenanceWindowActions.getMaintenanceWindowById(maintenanceWindowId)

        return renderMaintenanceWindowDetailsHeading(maintenanceWindow)
    }

    @Get("/fragments/list")
    @WebSecured
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Produces(MediaType.TEXT_HTML)
    fun maintenanceWindowList(request: HttpRequest<*>): HttpResponse<String> {
        val query = maintenanceWindowListQuery(request.queryParams())
        val maintenanceWindows = fetchListPage(query.pageRequest) { pageRequest ->
            maintenanceWindowActions.getMaintenanceWindowsPage(
                enabled = query.filters.enabled.value,
                name = query.filters.name.value,
                sorting = query.sorting,
                pageRequest = pageRequest,
            )
        }
        return pagedListFragment(query, maintenanceWindows) { shownQuery ->
            renderMaintenanceWindowList(maintenanceWindows, shownQuery, appGlobals)
        }
    }
}
