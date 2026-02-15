package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import com.kuvaszuptime.kuvasz.services.export.ExportHandler
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

@Controller("${API_V2_PREFIX}/monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.MONITORS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class MonitorController(
    private val httpMonitorActions: HttpMonitorActions,
    private val pushMonitorActions: PushMonitorActions,
    private val exportHandler: ExportHandler,
) : MonitorOperations {

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
        val export = mapOf(
            HttpMonitorConfig.CONFIG_PREFIX
                to httpMonitorActions.getHttpMonitorsExport().map { HttpMonitorExportDto.fromMonitorRecord(it) },
            PushMonitorConfig.CONFIG_PREFIX
                to pushMonitorActions.getPushMonitorsExport().map { PushMonitorExportDto.fromMonitorRecord(it) },
        )

        return exportHandler.createYamlFileFrom(fileNamePrefix = EXPORT_FILE_NAME_PREFIX, content = export)
    }

    companion object {
        private const val EXPORT_FILE_NAME_PREFIX = "kuvasz-monitors-export-"
    }
}
