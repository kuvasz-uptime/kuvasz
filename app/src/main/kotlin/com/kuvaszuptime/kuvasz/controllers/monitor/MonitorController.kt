package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.IcmpMonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.ReadOnlyMonitorException
import com.kuvaszuptime.kuvasz.models.dto.import.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import com.kuvaszuptime.kuvasz.services.export.ExportHandler
import com.kuvaszuptime.kuvasz.services.monitor.import.MonitorImportParser
import com.kuvaszuptime.kuvasz.services.monitor.import.MonitorImportService
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Part
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.multipart.CompletedFileUpload
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
import jakarta.validation.ValidationException

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
    private val icmpMonitorActions: IcmpMonitorActions,
    private val exportHandler: ExportHandler,
    private val monitorImportParser: MonitorImportParser,
    private val monitorImportService: MonitorImportService,
    private val appConfig: AppConfig,
) : MonitorOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(mediaType = MediaType.APPLICATION_YAML)],
        )
    )
    @Produces(MediaType.APPLICATION_YAML)
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getYamlMonitorsExport(): SystemFile {
        val export = mapOf(
            HttpMonitorConfig.CONFIG_PREFIX
                to httpMonitorActions.getHttpMonitorsExport().map { HttpMonitorExportDto.fromMonitorRecord(it) },
            PushMonitorConfig.CONFIG_PREFIX
                to pushMonitorActions.getPushMonitorsExport().map { PushMonitorExportDto.fromMonitorRecord(it) },
            IcmpMonitorConfig.CONFIG_PREFIX
                to icmpMonitorActions.getIcmpMonitorsExport().map { IcmpMonitorExportDto.fromMonitorRecord(it) },
        )

        return exportHandler.createYamlFileFrom(fileNamePrefix = EXPORT_FILE_NAME_PREFIX, content = export)
    }

    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun importYamlMonitors(
        @Part file: CompletedFileUpload,
        @QueryValue(defaultValue = "false") dryRun: Boolean,
    ): MonitorImportResultDto {
        val content = file.bytes
        if (content.isEmpty()) {
            throw ValidationException("The uploaded file is empty")
        }
        if (content.size > MAX_UPLOAD_SIZE_BYTES) {
            throw ValidationException("The uploaded file exceeds the maximum allowed size of 10 MB")
        }

        if (appConfig.isHttpMonitorExternalWriteDisabled() ||
            appConfig.isPushMonitorExternalWriteDisabled() ||
            appConfig.isIcmpMonitorExternalWriteDisabled()
        ) {
            throw ReadOnlyMonitorException()
        }

        val importDto = try {
            monitorImportParser.parse(content)
        } catch (e: Exception) {
            throw ValidationException("Failed to parse the uploaded YAML file: ${e.message}")
        }

        if (importDto.httpMonitors.isNullOrEmpty() &&
            importDto.pushMonitors.isNullOrEmpty() &&
            importDto.icmpMonitors.isNullOrEmpty()
        ) {
            throw ValidationException("The uploaded YAML file does not contain any monitors")
        }

        return monitorImportService.importMonitors(importDto, dryRun)
    }

    companion object {
        private const val EXPORT_FILE_NAME_PREFIX = "kuvasz-monitors-export-"
        private const val MAX_UPLOAD_SIZE_BYTES = 10 * 1024 * 1024 // 10 MB
    }
}
