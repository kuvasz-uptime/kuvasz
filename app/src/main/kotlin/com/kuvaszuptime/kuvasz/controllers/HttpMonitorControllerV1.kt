package com.kuvaszuptime.kuvasz.controllers

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.kuvaszuptime.kuvasz.config.HttpMonitorConfig
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorExportDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.models.dto.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorCrudService
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
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
import java.io.File
import java.time.Duration
import java.time.Instant

@Controller("$API_V1_PREFIX/monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = "HTTP monitors (V1, deprecated)")
@Deprecated("Use HttpMonitorControllerV2")
@SecurityRequirements(
    SecurityRequirement(name = "apiKey"),
    SecurityRequirement(name = "bearerAuth")
)
class HttpMonitorControllerV1(
    private val monitorCrudService: HttpMonitorCrudService,
    private val statCalculator: StatCalculator,
) : HttpMonitorOperationsV1 {

    private val yamlMapper = YAMLMapper()
        .registerModules(kotlinModule())
        .setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = HttpMonitorDetailsDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitorsWithDetails(
        @QueryValue enabled: Boolean?,
        @QueryValue uptimeStatus: List<UptimeStatus>?,
        @QueryValue sslStatus: List<SslStatus>?,
        @QueryValue sslCheckEnabled: Boolean?,
    ): List<HttpMonitorDetailsDto> =
        monitorCrudService.getMonitorsWithDetails(
            enabled = enabled,
            uptimeStatus = uptimeStatus.orEmpty(),
            sslStatus = sslStatus.orEmpty(),
            sslCheckEnabled = sslCheckEnabled,
        )

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = HttpMonitorDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitorDetails(monitorId: Long): HttpMonitorDetailsDto =
        monitorCrudService.getMonitorDetails(monitorId)

    @Status(HttpStatus.CREATED)
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Successful creation",
            content = [Content(schema = Schema(implementation = HttpMonitorDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = "Monitors are in read-only mode, because they are loaded from a YAML config file",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckHttpMonitorsWritable
    override fun createMonitor(@Valid monitor: HttpMonitorCreateDto): HttpMonitorDto {
        val createdMonitor = monitorCrudService.createMonitor(monitor)
        return HttpMonitorDto.fromMonitorRecord(createdMonitor)
    }

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
            description = "Monitors are in read-only mode, because they are loaded from a YAML config file",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckHttpMonitorsWritable
    override fun deleteMonitor(monitorId: Long) = monitorCrudService.deleteMonitorById(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update",
            content = [Content(schema = Schema(implementation = HttpMonitorDto::class))]
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
            description = "Monitors are in read-only mode, because they are loaded from a YAML config file",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckHttpMonitorsWritable
    override fun updateMonitor(monitorId: Long, updates: ObjectNode): HttpMonitorDto {
        val updatedMonitor = monitorCrudService.updateMonitor(monitorId, updates)
        return HttpMonitorDto.fromMonitorRecord(updatedMonitor)
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = HttpUptimeEventDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getUptimeEvents(monitorId: Long): List<HttpUptimeEventDto> =
        monitorCrudService.getUptimeEventsByMonitorId(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = SSLEventDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getSSLEvents(monitorId: Long): List<SSLEventDto> =
        monitorCrudService.getSSLEventsByMonitorId(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = HttpMonitorStatsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitorStats(
        monitorId: Long,
        @QueryValue period: Duration?,
    ): HttpMonitorStatsDto {
        val effectivePeriod = period ?: Duration.ofDays(MONITOR_STATS_PERIOD_DEFAULT_DAYS)
        return monitorCrudService.getMonitorStats(
            monitorId = monitorId,
            period = effectivePeriod,
        )
    }

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
            HttpMonitorConfig.LEGACY_CONFIG_PREFIX to monitorCrudService.getHttpMonitorsExport()
                .map { HttpMonitorExportDto.fromMonitorRecord(it) }
        )
        yamlMapper.writeValue(file, export)
        val finalFileName = EXPORT_FILE_NAME_PREFIX + Instant.now().epochSecond + EXPORT_FILE_EXTENSION

        return SystemFile(file, MediaType.APPLICATION_YAML_TYPE).attach(finalFileName)
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = HttpMonitoringStatsDto::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitoringStats(period: Duration?): HttpMonitoringStatsDto {
        return statCalculator.calculateOverallHttpStats(period ?: Duration.ofDays(MONITORING_STATS_PERIOD_DEFAULT_DAYS))
    }

    companion object {
        private const val MONITOR_STATS_PERIOD_DEFAULT_DAYS = 1L
        private const val MONITORING_STATS_PERIOD_DEFAULT_DAYS = 7L
        private const val EXPORT_FILE_NAME_PREFIX = "kuvasz-monitors-export-"
        private const val EXPORT_FILE_EXTENSION = ".yml"
    }
}
