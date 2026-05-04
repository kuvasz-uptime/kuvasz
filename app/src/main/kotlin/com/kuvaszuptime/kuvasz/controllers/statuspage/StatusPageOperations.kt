package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageCreateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageUpdateDto
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Patch
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.server.types.files.SystemFile
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody

interface StatusPageOperations {

    @Operation(summary = "Get all status pages")
    @Get("/")
    fun getStatusPages(
        @QueryValue("public") // Need to specify explicitly because of an internal bug in Micronaut
        @Parameter(required = false)
        public: Boolean?,
    ): List<StatusPageDto>

    @Operation(summary = "Get a status page")
    @Get("/{statusPageId}")
    fun getStatusPage(statusPageId: Long): StatusPageDto

    @Operation(
        summary = "Get a status page's details",
        description = "Use \"0\" as an ID to get the default status page's details. " +
            "The requests that are targeting public status pages doesn't need to be authenticated!"
    )
    @Get("/{statusPageId}/details")
    fun getStatusPageDetails(statusPageId: Long): StatusPageDetailsDto

    @Operation(summary = "Create a status page")
    @Post("/")
    fun createStatuspage(@Body statusPage: StatusPageCreateDto): StatusPageDto

    @Operation(summary = "Delete a status page by ID")
    @Delete("/{statusPageId}")
    fun deleteStatusPage(statusPageId: Long)

    @Operation(
        summary = "Update a status page by ID",
        description = "Updates the status page with the given ID. Only fields that are present in the request body " +
            "will be updated. Fields not present in the request body will remain unchanged.",
        requestBody = RequestBody(content = [Content(schema = Schema(implementation = StatusPageUpdateDto::class))])
    )
    @Patch("/{statusPageId}")
    fun updateStatusPage(statusPageId: Long, @Body updates: ObjectNode): StatusPageDto

    @Operation(summary = "Download the export of all status pages in YAML format")
    @Get("/export/yaml")
    fun getYamlStatusPagesExport(): SystemFile
}
