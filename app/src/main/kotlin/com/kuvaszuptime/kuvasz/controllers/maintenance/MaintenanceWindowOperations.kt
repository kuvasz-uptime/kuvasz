package com.kuvaszuptime.kuvasz.controllers.maintenance

import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowCreateDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowUpdateDto
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.types.files.SystemFile
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import tools.jackson.databind.node.ObjectNode

interface MaintenanceWindowOperations {

    @Operation(summary = "Get all maintenance windows")
    @Get("/")
    fun getMaintenanceWindows(): List<MaintenanceWindowDetailsDto>

    @Operation(summary = "Get a maintenance window")
    @Get("/{maintenanceWindowId}")
    fun getMaintenanceWindow(maintenanceWindowId: Long): MaintenanceWindowDetailsDto

    @Operation(summary = "Create a maintenance window")
    @Post("/")
    fun createMaintenanceWindow(@Body maintenanceWindow: MaintenanceWindowCreateDto): MaintenanceWindowDetailsDto

    @Operation(summary = "Delete a maintenance window by ID")
    @Delete("/{maintenanceWindowId}")
    fun deleteMaintenanceWindow(maintenanceWindowId: Long)

    @Operation(
        summary = "Update a maintenance window by ID",
        description = "Updates the maintenance window with the given ID. Only fields that are present in the request " +
            "body will be updated. Fields not present in the request body will remain unchanged.",
        requestBody = RequestBody(
            content = [Content(schema = Schema(implementation = MaintenanceWindowUpdateDto::class))]
        )
    )
    @Patch("/{maintenanceWindowId}")
    fun updateMaintenanceWindow(maintenanceWindowId: Long, @Body updates: ObjectNode): MaintenanceWindowDetailsDto

    @Operation(summary = "Download the export of all maintenance windows in YAML format")
    @Get("/export/yaml")
    fun getYamlMaintenanceWindowsExport(): SystemFile
}
