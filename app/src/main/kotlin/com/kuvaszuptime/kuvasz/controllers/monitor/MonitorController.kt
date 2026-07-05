package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.config.AppConfig
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.config.IcmpMonitorConfig
import com.kuvaszuptime.kuvasz.config.PushMonitorConfig
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.importing.HttpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.IcmpMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportDto
import com.kuvaszuptime.kuvasz.models.dto.importing.MonitorImportResultDto
import com.kuvaszuptime.kuvasz.models.dto.importing.PushMonitorImportAdapter
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorExportDto
import com.kuvaszuptime.kuvasz.models.monitor.http.HttpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.icmp.IcmpMonitorCreator
import com.kuvaszuptime.kuvasz.models.monitor.push.PushMonitorCreator
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
import com.kuvaszuptime.kuvasz.services.export.ExportHandler
import com.kuvaszuptime.kuvasz.services.monitor.MonitorImporter
import com.kuvaszuptime.kuvasz.validation.throwIfNotEmpty
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
import io.micronaut.validation.validator.Validator
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.ValidationException
import tools.jackson.dataformat.yaml.YAMLMapper

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
    private val monitorImporter: MonitorImporter,
    private val yamlMapper: YAMLMapper,
    private val validator: Validator,
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

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful import or dry-run preview",
            content = [Content(schema = Schema(implementation = MonitorImportResultDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ExecuteOn(TaskExecutors.BLOCKING)
    @Suppress("TooGenericExceptionCaught")
    override fun importYamlMonitors(
        @Part file: CompletedFileUpload,
        @QueryValue(defaultValue = "false") dryRun: Boolean,
    ): MonitorImportResultDto {
        val importDto = try {
            yamlMapper.readValue(file.bytes, MonitorImportDto::class.java)
        } catch (e: Exception) {
            throw ValidationException("Failed to parse the uploaded YAML file: ${e.message}", e)
        } ?: MonitorImportDto()
        val httpMonitors: List<HttpMonitorCreator> =
            importDto.httpMonitors.orEmpty().map { HttpMonitorImportAdapter(it) }
        val pushMonitors: List<PushMonitorCreator> =
            importDto.pushMonitors.orEmpty().map { PushMonitorImportAdapter(it) }
        val icmpMonitors: List<IcmpMonitorCreator> =
            importDto.icmpMonitors.orEmpty().map { IcmpMonitorImportAdapter(it) }

        validateMonitors(httpMonitors, pushMonitors, icmpMonitors)

        val perTypeResults = buildList {
            if (httpMonitors.isNotEmpty() && !appConfig.isHttpMonitorExternalWriteDisabled()) {
                add(monitorImporter.importHttpMonitorConfigs(httpMonitors, dryRun))
            }
            if (pushMonitors.isNotEmpty() && !appConfig.isPushMonitorExternalWriteDisabled()) {
                add(monitorImporter.importPushMonitorConfigs(pushMonitors, dryRun))
            }
            if (icmpMonitors.isNotEmpty() && !appConfig.isIcmpMonitorExternalWriteDisabled()) {
                add(monitorImporter.importIcmpMonitorConfigs(icmpMonitors, dryRun))
            }
        }

        return MonitorImportResultDto(
            receivedMonitorCnt = perTypeResults.sumOf { it.receivedMonitorCnt },
            importedMonitorCnt = perTypeResults.sumOf { it.importedMonitorCnt },
            deletedMonitorCount = perTypeResults.sumOf { it.deletedMonitorCount },
            dryRun = dryRun,
            perTypeResults = perTypeResults,
        )
    }

    private fun validateMonitors(
        httpMonitors: List<HttpMonitorCreator>,
        pushMonitors: List<PushMonitorCreator>,
        icmpMonitors: List<IcmpMonitorCreator>,
    ) {
        httpMonitors.forEach { validator.validate(it).throwIfNotEmpty() }
        pushMonitors.forEach { validator.validate(it).throwIfNotEmpty() }
        icmpMonitors.forEach { validator.validate(it).throwIfNotEmpty() }
    }

    companion object {
        private const val EXPORT_FILE_NAME_PREFIX = "kuvasz-monitors-export-"
    }
}
