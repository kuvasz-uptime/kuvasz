package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.MonitorDetails
import com.kuvaszuptime.kuvasz.models.MonitorNotFoundError
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.repositories.MonitorRepository
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import javax.inject.Inject

@Controller("/monitor", produces = [MediaType.APPLICATION_JSON])
@Tag(name = "Monitor operations")
class MonitorController @Inject constructor(
    private val monitorRepository: MonitorRepository
) : MonitorOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = MonitorDetails::class)))]
        )
    )
    override fun getMonitors(@QueryValue enabledOnly: Boolean?): List<MonitorDetails> =
        monitorRepository.getMonitorDetails(enabledOnly ?: false)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = MonitorDetails::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    override fun getMonitor(monitorId: Int): MonitorDetails =
        monitorRepository.getMonitorDetails(monitorId).fold(
            { throw MonitorNotFoundError(monitorId) },
            { it }
        )
}
