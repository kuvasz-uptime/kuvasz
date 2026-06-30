package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.controllers.maintenance.CheckMaintenanceWindowsWritable
import com.kuvaszuptime.kuvasz.mcp.ToolNames.GET_APP_SETTINGS
import com.kuvaszuptime.kuvasz.mcp.ToolNames.LIST_INTEGRATIONS
import com.kuvaszuptime.kuvasz.mcp.schemas.DeleteResultSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.MaintenanceWindowCreatorSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.MaintenanceWindowListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.MaintenanceWindowSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.MaintenanceWindowSummarySchema
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowActions
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import io.micronaut.validation.validator.Validator
import jakarta.inject.Singleton

@Singleton
class MaintenanceWindowTools(
    private val maintenanceWindowActions: MaintenanceWindowActions,
    private val validator: Validator,
) {

    @Tool(
        name = ToolNames.LIST_MAINTENANCE_WINDOWS,
        description = "Lists all maintenance windows configured in Kuvasz with their schedule and current state. " +
            "A window can be manual (toggled via 'enabled'), recurring (cron-based) or one-off (single 'start'). " +
            "The 'active' flag tells whether the window is suppressing checks right now.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listMaintenanceWindows(): MaintenanceWindowListSchema =
        MaintenanceWindowListSchema(
            maintenanceWindows = maintenanceWindowActions.getMaintenanceWindows()
                .map { MaintenanceWindowSummarySchema.fromDto(it) }
        )

    @Tool(
        name = ToolNames.GET_MAINTENANCE_WINDOW_DETAILS,
        description = "Get the full details of a specific maintenance window by its ID, including the affected " +
            "monitors, the notified integrations, and the resolved next start / end times.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getMaintenanceWindowDetails(
        @ToolArg(description = "The numeric ID of the maintenance window") maintenanceWindowId: Long,
    ): MaintenanceWindowSchema =
        MaintenanceWindowSchema.fromDto(maintenanceWindowActions.getMaintenanceWindowById(maintenanceWindowId))

    @Tool(
        name = ToolNames.CREATE_MAINTENANCE_WINDOW,
        description = "Creates a new maintenance window. Only 'name' is always required. The schedule must describe " +
            "exactly one window type: provide neither 'cron' nor 'start' for a manual window; 'cron' + 'duration' " +
            "for a recurring window; or 'start' + 'duration' for a one-off window. " +
            "Refer to the docs for the available fields and defaults: " +
            "https://kuvasz-uptime.dev/management/maintenance-windows/. " +
            "Referenced monitors that do not exist are silently dropped; the available integrations can be found via " +
            "the $LIST_INTEGRATIONS tool. " +
            "This tool will work only if 'areMaintenanceWindowsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = false)
    )
    @CheckMaintenanceWindowsWritable
    fun createMaintenanceWindow(input: MaintenanceWindowCreatorSchema): MaintenanceWindowSchema {
        val createDto = input.toDto()
        validator.validate(createDto).throwIfNotEmpty()
        return MaintenanceWindowSchema.fromDto(maintenanceWindowActions.createMaintenanceWindow(createDto))
    }

    @Tool(
        name = ToolNames.TOGGLE_MAINTENANCE_WINDOW,
        description = "Enables or disables a maintenance window. 'enabled' is the master on/off switch: for " +
            "recurring and one-off windows the schedule still decides whether they are currently active. " +
            "This tool will work only if 'areMaintenanceWindowsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true)
    )
    @CheckMaintenanceWindowsWritable
    fun toggleMaintenanceWindow(
        @ToolArg(description = "The numeric ID of the maintenance window") maintenanceWindowId: Long,
        @ToolArg(description = "Set to true to enable the window, false to disable it") enabled: Boolean,
    ): MaintenanceWindowSchema =
        MaintenanceWindowSchema.fromDto(
            maintenanceWindowActions.updateMaintenanceWindow(maintenanceWindowId, monitorToggleUpdate(enabled))
        )

    @Tool(
        name = ToolNames.DELETE_MAINTENANCE_WINDOW,
        description = "Permanently deletes a maintenance window by its ID. " +
            "This tool will work only if 'areMaintenanceWindowsReadOnly' from the $GET_APP_SETTINGS tool " +
            "call is 'false', otherwise it will return with an error.",
        annotations = Tool.ToolAnnotations(readOnlyHint = false, destructiveHint = true, idempotentHint = true)
    )
    @CheckMaintenanceWindowsWritable
    fun deleteMaintenanceWindow(
        @ToolArg(description = "The numeric ID of the maintenance window") maintenanceWindowId: Long,
    ): DeleteResultSchema {
        maintenanceWindowActions.deleteMaintenanceWindowById(maintenanceWindowId)
        return DeleteResultSchema(deleted = true, id = maintenanceWindowId)
    }
}
