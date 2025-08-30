package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.IncidentDto
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Duration

interface IncidentOperations {

    @Operation(summary = "Get all incidents")
    @Get("/")
    fun getIncidents(
        @QueryValue
        @Parameter(required = false)
        monitorId: Long?,
        @QueryValue
        @Parameter(
            required = false,
            schema = Schema(
                implementation = Duration::class,
                description = "An ISO-8601 Duration string, default P7D",
            )
        )
        period: Duration?,
        @QueryValue
        @Parameter(
            required = false,
            description = "If false, only ongoing incidents are returned, default true",
        )
        includeResolved: Boolean?,
    ): List<IncidentDto>
}
