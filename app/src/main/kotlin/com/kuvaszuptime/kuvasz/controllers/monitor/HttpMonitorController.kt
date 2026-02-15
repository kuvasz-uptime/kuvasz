package com.kuvaszuptime.kuvasz.controllers.monitor

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.jooq.enums.SslStatus
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.event.HttpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.event.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDocs.MONITORS_405_REASON
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.http.HttpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.http.HttpMonitorActions
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
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
import java.time.Duration

@Controller("${API_V2_PREFIX}/http-monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.HTTP_MONITORS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class HttpMonitorController(
    private val monitorActions: HttpMonitorActions,
    private val statCalculator: StatCalculator,
) : HttpMonitorOperations {

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
        monitorActions.getMonitorsWithDetails(
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
        monitorActions.getMonitorDetails(monitorId)

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
            description = MONITORS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckHttpMonitorsWritable
    override fun createMonitor(@Valid monitor: HttpMonitorCreateDto): HttpMonitorDto {
        val createdMonitor = monitorActions.createMonitor(monitor)
        return HttpMonitorDto.fromMonitorRecord(createdMonitor)
    }

    @Status(HttpStatus.NO_CONTENT)
    @ApiResponses(
        ApiResponse(
            responseCode = "204",
            description = "Successful deletion"
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
            description = MONITORS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckHttpMonitorsWritable
    override fun deleteMonitor(monitorId: Long) = monitorActions.deleteMonitorById(monitorId)

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
            description = MONITORS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckHttpMonitorsWritable
    override fun updateMonitor(monitorId: Long, updates: ObjectNode): HttpMonitorDto {
        val updatedMonitor = monitorActions.updateMonitor(monitorId, updates)
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
        monitorActions.getUptimeEventsByMonitorId(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = SSLEventDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getSSLEvents(monitorId: Long): List<SSLEventDto> =
        monitorActions.getSSLEventsByMonitorId(monitorId)

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
        return monitorActions.getMonitorStats(
            monitorId = monitorId,
            period = effectivePeriod,
        )
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
    }
}
