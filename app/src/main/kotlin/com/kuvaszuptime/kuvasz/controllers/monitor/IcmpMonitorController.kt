package com.kuvaszuptime.kuvasz.controllers.monitor

import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.jooq.enums.UptimeStatus
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.event.IcmpUptimeEventDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.IcmpMonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDocs.MONITORS_405_REASON
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitorStatsDto
import com.kuvaszuptime.kuvasz.models.dto.monitor.icmp.IcmpMonitoringStatsDto
import com.kuvaszuptime.kuvasz.services.StatCalculator
import com.kuvaszuptime.kuvasz.services.check.icmp.IcmpMonitorActions
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
import tools.jackson.databind.node.ObjectNode
import java.time.Duration

@Controller("${API_V2_PREFIX}/icmp-monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.ICMP_MONITORS)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class IcmpMonitorController(
    private val monitorActions: IcmpMonitorActions,
    private val statCalculator: StatCalculator,
) : IcmpMonitorOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = IcmpMonitorDetailsDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMonitorsWithDetails(
        @QueryValue enabled: Boolean?,
        @QueryValue uptimeStatus: List<UptimeStatus>?,
    ): List<IcmpMonitorDetailsDto> =
        monitorActions.getMonitorsWithDetails(
            enabled = enabled,
            uptimeStatus = uptimeStatus.orEmpty(),
        )

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = IcmpMonitorDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMonitorDetails(monitorId: Long): IcmpMonitorDetailsDto =
        monitorActions.getMonitorDetails(monitorId)

    @Status(HttpStatus.CREATED)
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Successful creation",
            content = [Content(schema = Schema(implementation = IcmpMonitorDto::class))]
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
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckIcmpMonitorsWritable
    override fun createMonitor(@Valid monitor: IcmpMonitorCreateDto): IcmpMonitorDto {
        val createdMonitor = monitorActions.createMonitor(monitor)
        return IcmpMonitorDto.fromMonitorRecord(createdMonitor)
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
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckIcmpMonitorsWritable
    override fun deleteMonitor(monitorId: Long) = monitorActions.deleteMonitorById(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update",
            content = [Content(schema = Schema(implementation = IcmpMonitorDto::class))]
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
    @ExecuteOn(TaskExecutors.BLOCKING)
    @CheckIcmpMonitorsWritable
    override fun updateMonitor(monitorId: Long, updates: ObjectNode): IcmpMonitorDto {
        val updatedMonitor = monitorActions.updateMonitor(monitorId, updates)
        return IcmpMonitorDto.fromMonitorRecord(updatedMonitor)
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = IcmpUptimeEventDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getUptimeEvents(monitorId: Long): List<IcmpUptimeEventDto> =
        monitorActions.getUptimeEventsByMonitorId(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = IcmpMonitorStatsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMonitorStats(
        monitorId: Long,
        @QueryValue period: Duration?,
    ): IcmpMonitorStatsDto {
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
            content = [Content(schema = Schema(implementation = IcmpMonitoringStatsDto::class))]
        )
    )
    @ExecuteOn(TaskExecutors.BLOCKING)
    override fun getMonitoringStats(period: Duration?): IcmpMonitoringStatsDto {
        return statCalculator.calculateOverallIcmpStats(period ?: Duration.ofDays(MONITORING_STATS_PERIOD_DEFAULT_DAYS))
    }

    companion object {
        private const val MONITOR_STATS_PERIOD_DEFAULT_DAYS = 1L
        private const val MONITORING_STATS_PERIOD_DEFAULT_DAYS = 7L
    }
}
