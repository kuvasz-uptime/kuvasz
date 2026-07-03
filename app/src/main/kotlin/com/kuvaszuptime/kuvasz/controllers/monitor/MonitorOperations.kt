package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportResultDto
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.multipart.CompletedFileUpload
import io.micronaut.http.server.types.files.SystemFile
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses

interface MonitorOperations {

    @Operation(summary = "Download the export of all monitors in YAML format")
    @Get("/export/yaml")
    fun getYamlMonitorsExport(): SystemFile

    @Operation(
        summary = "Import monitors from a YAML backup file",
        description = "Upload a YAML monitor backup. Existing monitors with the same name will be updated, " +
            "and monitors not present in the backup will be deleted. Use dryRun=true to preview the outcome " +
            "without making any changes.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful import or dry-run preview",
            content = [Content(schema = Schema(implementation = MonitorImportResultDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = com.kuvaszuptime.kuvasz.models.ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = "Monitor type is managed via external YAML config (read-only)",
            content = [Content(schema = Schema(implementation = com.kuvaszuptime.kuvasz.models.ServiceError::class))]
        )
    )
    @Post("/import/yaml", consumes = [MediaType.MULTIPART_FORM_DATA])
    fun importYamlMonitors(
        @Part file: CompletedFileUpload,
        @QueryValue(defaultValue = "false") dryRun: Boolean,
    ): MonitorImportResultDto
}
