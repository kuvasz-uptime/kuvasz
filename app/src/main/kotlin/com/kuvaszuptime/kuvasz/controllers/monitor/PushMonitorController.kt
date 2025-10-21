package com.kuvaszuptime.kuvasz.controllers.monitor

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.event.PushUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDocs
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.push.PushMonitoringStatsDto
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.push.PushMonitorActions
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

@Controller("${API_V2_PREFIX}/push-monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.PUSH_MONITORS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class PushMonitorController(
    private val monitorActions: PushMonitorActions,
    private val statCalculator: StatCalculator,
) : PushMonitorOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = PushMonitorDetailsDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitorsWithDetails(
        @QueryValue enabled: Boolean?,
        @QueryValue uptimeStatus: List<UptimeStatus>?,
    ): List<PushMonitorDetailsDto> =
        monitorActions.getMonitorsWithDetails(
            enabled = enabled,
            uptimeStatus = uptimeStatus.orEmpty(),
        )

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = PushMonitorDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitorDetails(monitorId: Long): PushMonitorDetailsDto =
        monitorActions.getMonitorDetails(monitorId)

    @Status(HttpStatus.CREATED)
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Successful creation",
            content = [Content(schema = Schema(implementation = PushMonitorDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = PushMonitorDocs.MONITORS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckPushMonitorsWritable
    override fun createMonitor(@Valid monitor: PushMonitorCreateDto): PushMonitorDto {
        val createdMonitor = monitorActions.createMonitor(monitor)
        return PushMonitorDto.fromMonitorRecord(createdMonitor)
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
            description = PushMonitorDocs.MONITORS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckPushMonitorsWritable
    override fun deleteMonitor(monitorId: Long) = monitorActions.deleteMonitorById(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update",
            content = [Content(schema = Schema(implementation = PushMonitorDto::class))]
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
            description = PushMonitorDocs.MONITORS_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckPushMonitorsWritable
    override fun updateMonitor(monitorId: Long, updates: ObjectNode): PushMonitorDto {
        val updatedMonitor = monitorActions.updateMonitor(monitorId, updates)
        return PushMonitorDto.fromMonitorRecord(updatedMonitor)
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = PushUptimeEventDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getUptimeEvents(monitorId: Long): List<PushUptimeEventDto> =
        monitorActions.getUptimeEventsByMonitorId(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = PushMonitorStatsDto::class))]
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
    ): PushMonitorStatsDto {
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
            content = [Content(schema = Schema(implementation = PushMonitoringStatsDto::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitoringStats(period: Duration?): PushMonitoringStatsDto {
        return statCalculator.calculateOverallPushStats(period ?: Duration.ofDays(MONITORING_STATS_PERIOD_DEFAULT_DAYS))
    }

    companion object {
        private const val MONITOR_STATS_PERIOD_DEFAULT_DAYS = 1L
        private const val MONITORING_STATS_PERIOD_DEFAULT_DAYS = 7L
    }
}
