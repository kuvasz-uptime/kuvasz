package com.kuvaszuptime.kuvasz.controllers

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorCrudService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.types.files.SystemFile
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import java.io.File
import java.time.Instant

@Controller("$API_V2_PREFIX/monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = "Monitors")
@SecurityRequirements(
    SecurityRequirement(name = "apiKey"),
    SecurityRequirement(name = "bearerAuth")
)
class MonitorControllerV2(
    private val monitorCrudService: HttpMonitorCrudService,
) : MonitorOperationsV2 {

    private val yamlMapper = YAMLMapper()
        .registerModules(kotlinModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(mediaType = MediaType.APPLICATION_YAML)],
        )
    )
    @Produces(MediaType.APPLICATION_YAML)
    @ExecuteOn(TaskExecutors.IO)
    override fun getYamlMonitorsExport(): SystemFile {
        val file = File.createTempFile("temp", EXPORT_FILE_NAME_PREFIX)
        val export = mapOf(
            HttpMonitorConfig.CONFIG_PREFIX to monitorCrudService.getHttpMonitorsExport()
                .map { HttpMonitorExportDto.fromMonitorRecord(it) }
        )
        yamlMapper.writeValue(file, export)
        val finalFileName = EXPORT_FILE_NAME_PREFIX + Instant.now().epochSecond + EXPORT_FILE_EXTENSION

        return SystemFile(file, MediaType.APPLICATION_YAML_TYPE).attach(finalFileName)
    }

    companion object {
        private const val EXPORT_FILE_NAME_PREFIX = "kuvasz-monitors-export-"
        private const val EXPORT_FILE_EXTENSION = ".yml"
    }
}
