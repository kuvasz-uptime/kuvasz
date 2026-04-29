package com.kuvaszuptime.kuvasz.controllers.statuspage

import com.fasterxml.jackson.databind.node.ObjectNode
import com.kuvaszuptime.kuvasz.OpenApiSecuritySchemes
import com.kuvaszuptime.kuvasz.OpenApiTags
import com.kuvaszuptime.kuvasz.config.DefaultStatusPageConfig
import com.kuvaszuptime.kuvasz.config.StatusPageConfig
import com.kuvaszuptime.kuvasz.controllers.API_V2_PREFIX
import com.kuvaszuptime.kuvasz.models.ServiceError
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageCreateDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDetailsDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDocs.STATUS_PAGES_405_REASON
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageDto
import com.kuvaszuptime.kuvasz.models.dto.statuspage.StatusPageExportDto
import com.kuvaszuptime.kuvasz.security.Role
import com.kuvaszuptime.kuvasz.services.export.ExportHandler
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageActions
import com.kuvaszuptime.kuvasz.services.statuspage.StatusPageDataActions
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.Status
import io.micronaut.http.server.types.files.SystemFile
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.utils.SecurityService
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

@Controller("${API_V2_PREFIX}/status-pages", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = OpenApiTags.STATUS_PAGES)
@SecurityRequirements(
    SecurityRequirement(name = OpenApiSecuritySchemes.API_KEY),
    SecurityRequirement(name = OpenApiSecuritySchemes.BEARER_AUTH)
)
class StatusPageController(
    private val statusPageActions: StatusPageActions,
    private val statusPageDataActions: StatusPageDataActions,
    private val exportHandler: ExportHandler,
    private val defaultStatusPageConfig: DefaultStatusPageConfig,
    private val securityService: SecurityService?,
) : StatusPageOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(array = ArraySchema(schema = Schema(implementation = StatusPageDto::class)))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getStatusPages(public: Boolean?): List<StatusPageDto> = statusPageActions.getStatusPages(public)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = StatusPageDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getStatusPage(statusPageId: Long): StatusPageDto = statusPageActions.getStatusPageById(statusPageId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = StatusPageDetailsDto::class))]
        ),
        ApiResponse(
            responseCode = "404",
            description = "Not found",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @Secured(SecurityRule.IS_ANONYMOUS)
    override fun getStatusPageDetails(statusPageId: Long): StatusPageDetailsDto =
        if (statusPageId == 0L) {
            if (!defaultStatusPageConfig.public && !hasApiRole()) {
                throw AuthorizationException(null)
            }

            val dataDto = statusPageDataActions.getCachedDefaultStatusPageData()
            StatusPageDetailsDto(
                id = 0L,
                title = defaultStatusPageConfig.title,
                slug = null,
                customLogoUrl = defaultStatusPageConfig.customLogoUrl,
                customFaviconUrl = defaultStatusPageConfig.customFaviconUrl,
                public = defaultStatusPageConfig.public,
                systemStatus = dataDto.systemStatus,
                generatedAt = dataDto.generatedAt,
                monitors = dataDto.monitors,
            )
        } else {
            val pageDto = statusPageActions.getStatusPageById(statusPageId)
            if (!pageDto.public && !hasApiRole()) {
                throw AuthorizationException(null)
            }
            val dataDto = statusPageDataActions.getCachedStatusPageData(pageDto.id)

            StatusPageDetailsDto(
                id = pageDto.id,
                title = pageDto.title,
                slug = pageDto.slug,
                customLogoUrl = pageDto.customLogoUrl,
                customFaviconUrl = pageDto.customFaviconUrl,
                public = pageDto.public,
                systemStatus = dataDto.systemStatus,
                generatedAt = dataDto.generatedAt,
                monitors = dataDto.monitors,
            )
        }

    @Status(HttpStatus.CREATED)
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Successful creation",
            content = [Content(schema = Schema(implementation = StatusPageDto::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Bad request",
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        ),
        ApiResponse(
            responseCode = "405",
            description = STATUS_PAGES_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckStatusPagesWritable
    override fun createStatuspage(@Valid statusPage: StatusPageCreateDto): StatusPageDto {
        val created = statusPageActions.createStatusPage(statusPage)
        return StatusPageDto.fromStatusPageRecord(created)
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
            description = STATUS_PAGES_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckStatusPagesWritable
    override fun deleteStatusPage(statusPageId: Long) = statusPageActions.deleteStatusPageById(statusPageId)

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful update",
            content = [Content(schema = Schema(implementation = StatusPageDto::class))]
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
            description = STATUS_PAGES_405_REASON,
            content = [Content(schema = Schema(implementation = ServiceError::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    @CheckStatusPagesWritable
    override fun updateStatusPage(
        statusPageId: Long,
        updates: ObjectNode
    ): StatusPageDto {
        val updated = statusPageActions.updateStatusPage(statusPageId, updates)
        return StatusPageDto.fromStatusPageRecord(updated)
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
    override fun getYamlStatusPagesExport(): SystemFile {
        val export = mapOf(
            StatusPageConfig.CONFIG_PREFIX to statusPageActions.getStatusPagesExport()
                .map { StatusPageExportDto.fromStatusPageRecord(it) }
        )

        return exportHandler.createYamlFileFrom(fileNamePrefix = EXPORT_FILE_NAME_PREFIX, content = export)
    }

    companion object {
        private const val EXPORT_FILE_NAME_PREFIX = "kuvasz-status-pages-export-"
    }

    private fun hasApiRole() = securityService?.hasRole(Role.API.alias) == true
}
