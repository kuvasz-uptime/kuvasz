package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.MonitorNotFoundError
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.MonitorCreateDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorDto
import com.kuvaszuptime.kuvasz.models.dto.MonitorUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.PagerdutyKeyUpdateDto
import com.kuvaszuptime.kuvasz.models.dto.SSLEventDto
import com.kuvaszuptime.kuvasz.models.dto.UptimeEventDto
import com.kuvaszuptime.kuvasz.services.MonitorCrudService
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.Status
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("/monitors", produces = [MediaType.APPLICATION_JSON])
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
    override fun createMonitor(monitor: MonitorCreateDto): MonitorDto {
        val updatedPojo = monitorCrudService.createMonitor(monitor)
        return MonitorDto.fromMonitorPojo(updatedPojo)
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
    override fun updateMonitor(monitorId: Int, monitorUpdateDto: MonitorUpdateDto): MonitorDto {
        val updatedPojo = monitorCrudService.updateMonitor(monitorId, monitorUpdateDto)
        return MonitorDto.fromMonitorPojo(updatedPojo)
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
    override fun upsertPagerdutyIntegrationKey(monitorId: Int, upsertDto: PagerdutyKeyUpdateDto): MonitorDto {
        val updatedPojo = monitorCrudService.updatePagerdutyIntegrationKey(monitorId, upsertDto.pagerdutyIntegrationKey)
        return MonitorDto.fromMonitorPojo(updatedPojo)
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
    override fun getUptimeEvents(monitorId: Int): List<UptimeEventDto> =
        monitorCrudService.getUptimeEventsByMonitorId(monitorId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = SSLEventDto::class)))]
        )
    )
    override fun getSSLEvents(monitorId: Int): List<SSLEventDto> = monitorCrudService.getSSLEventsByMonitorId(monitorId)
}
