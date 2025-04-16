package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.MonitorNotFoundError
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.*
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
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
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid

@Controller("/monitors", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = "Monitor operations")
@SecurityRequirement(name = "bearerAuth")
class MonitorController(
    private val monitorCrudService: MonitorCrudService
) : MonitorOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = MonitorDetailsDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitorsWithDetails(@QueryValue enabledOnly: Boolean?): List<MonitorDetailsDto> =
        monitorCrudService.getMonitorsWithDetails(enabledOnly ?: false)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = MonitorDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getMonitorDetails(monitorId: Int): MonitorDetailsDto =
        monitorCrudService.getMonitorDetails(monitorId) ?: throw MonitorNotFoundError(monitorId)

    @Status(HttpStatus.CREATED)
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Successful creation",
            content = [Content(schema = Schema(implementation = MonitorDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun createMonitor(@Valid monitor: MonitorCreateDto): MonitorDto {
        val createdMonitor = monitorCrudService.createMonitor(monitor)
        return MonitorDto.fromMonitorRecord(createdMonitor)
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
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun deleteMonitor(monitorId: Int) = monitorCrudService.deleteMonitorById(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update",
            content = [Content(schema = Schema(implementation = MonitorDto::class))]
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
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun updateMonitor(monitorId: Int, @Valid monitorUpdateDto: MonitorUpdateDto): MonitorDto {
        val updatedMonitor = monitorCrudService.updateMonitor(monitorId, monitorUpdateDto)
        return MonitorDto.fromMonitorRecord(updatedMonitor)
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update or create",
            content = [Content(schema = Schema(implementation = MonitorDto::class))]
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
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun upsertPagerdutyIntegrationKey(monitorId: Int, @Valid upsertDto: PagerdutyKeyUpdateDto): MonitorDto {
        val updatedMonitor = monitorCrudService.updatePagerdutyIntegrationKey(
            monitorId,
            upsertDto.pagerdutyIntegrationKey,
        )
        return MonitorDto.fromMonitorRecord(updatedMonitor)
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
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun deletePagerdutyIntegrationKey(monitorId: Int) {
        monitorCrudService.updatePagerdutyIntegrationKey(monitorId, null)
    }

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = UptimeEventDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getUptimeEvents(monitorId: Int): List<UptimeEventDto> =
        monitorCrudService.getUptimeEventsByMonitorId(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = SSLEventDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getSSLEvents(monitorId: Int): List<SSLEventDto> = monitorCrudService.getSSLEventsByMonitorId(monitorId)
}
