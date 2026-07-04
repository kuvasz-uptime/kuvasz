package com.kuvaszuptime.kuvasz.controllers.maintenance

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.config.MaintenanceWindowConfig
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowCreateDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDocs.MAINTENANCE_WINDOWS_405_REASON
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowExportDto
import com.kuvaszuptime.kuvasz.services.export.ExportHandler
import com.kuvaszuptime.kuvasz.services.maintenance.MaintenanceWindowActions
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Status
import io.micronaut.http.server.types.files.SystemFile
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import tools.jackson.databind.node.ObjectNode

@Controller("${API_V2_PREFIX}/maintenance-windows", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.MAINTENANCE_WINDOWS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class MaintenanceWindowController(
    private val maintenanceWindowActions: MaintenanceWindowActions,
    private val exportHandler: ExportHandler,
) : MaintenanceWindowOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [
                Content(array = ArraySchema(schema = Schema(implementation = MaintenanceWindowDetailsDto::class)))
            ]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMaintenanceWindows(): List<MaintenanceWindowDetailsDto> =
        maintenanceWindowActions.getMaintenanceWindows()

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = MaintenanceWindowDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMaintenanceWindow(maintenanceWindowId: Long): MaintenanceWindowDetailsDto =
        maintenanceWindowActions.getMaintenanceWindowById(maintenanceWindowId)

    @Status(HttpStatus.CREATED)
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Successful creation",
            content = [Content(schema = Schema(implementation = MaintenanceWindowDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = MAINTENANCE_WINDOWS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckMaintenanceWindowsWritable
    override fun createMaintenanceWindow(
        @Valid maintenanceWindow: MaintenanceWindowCreateDto
    ): MaintenanceWindowDetailsDto = maintenanceWindowActions.createMaintenanceWindow(maintenanceWindow)

    @Status(HttpStatus.NO_CONTENT)
    @ApiResponses(
        ApiResponse(
            responseCode = "204",
            description = "Successful deletion"
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = MAINTENANCE_WINDOWS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckMaintenanceWindowsWritable
    override fun deleteMaintenanceWindow(maintenanceWindowId: Long) =
        maintenanceWindowActions.deleteMaintenanceWindowById(maintenanceWindowId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update",
            content = [Content(schema = Schema(implementation = MaintenanceWindowDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = MAINTENANCE_WINDOWS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckMaintenanceWindowsWritable
    override fun updateMaintenanceWindow(
        maintenanceWindowId: Long,
        updates: ObjectNode
    ): MaintenanceWindowDetailsDto = maintenanceWindowActions.updateMaintenanceWindow(maintenanceWindowId, updates)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(mediaType = MediaType.APPLICATION_YAML)],
        )
    )
    @Produces(MediaType.APPLICATION_YAML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getYamlMaintenanceWindowsExport(): SystemFile {
        val export = mapOf(
            MaintenanceWindowConfig.CONFIG_PREFIX to maintenanceWindowActions.getMaintenanceWindowsExport()
                .map { MaintenanceWindowExportDto.fromRecord(it) }
        )

        return exportHandler.createYamlFileFrom(fileNamePrefix = EXPORT_FILE_NAME_PREFIX, content = export)
    }

    companion object {
        private const val EXPORT_FILE_NAME_PREFIX = "kuvasz-maintenance-windows-export-"
    }
}
