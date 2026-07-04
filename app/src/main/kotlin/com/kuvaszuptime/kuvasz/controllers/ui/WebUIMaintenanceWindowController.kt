package com.kuvaszuptime.kuvasz.controllers.ui

import com.kuvaszuptime.kuvasz.AppGlobals
import com.kuvaszuptime.kuvasz.jooq.tables.MaintenanceWindow.MAINTENANCE_WINDOW
import com.kuvaszuptime.kuvasz.security.ui.WebSecured
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowActions
import com.kuvaszuptime.kuvasz.services.monitor.SharedMonitorActions
import com.kuvaszuptime.kuvasz.ui.fragments.maintenance.*
import com.kuvaszuptime.kuvasz.ui.pages.maintenance.*
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
    private val sharedMonitorActions: SharedMonitorActions,
    private val appGlobals: AppGlobals,
) {

    @Get("/")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun maintenanceWindows() = renderMaintenanceWindowsPage(appGlobals)

    @Get("/{maintenanceWindowId}")
    @WebSecured
    @Produces(MediaType.TEXT_HTML)
    fun maintenanceWindowDetails(@PathVariable maintenanceWindowId: Long): String {
        val maintenanceWindow = maintenanceWindowActions.getMaintenanceWindowById(maintenanceWindowId)

        return renderMaintenanceWindowDetailsPage(
            globals = appGlobals,
            maintenanceWindow = maintenanceWindow,
            monitorIds = sharedMonitorActions.getConfiguredMonitorIds(),
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
    fun maintenanceWindowList(): String {
        val maintenanceWindows =
            maintenanceWindowActions.getMaintenanceWindows(sortedBy = MAINTENANCE_WINDOW.NAME.asc())

        return renderMaintenanceWindowList(
            maintenanceWindows = maintenanceWindows,
            appGlobals = appGlobals,
        )
    }
}
