package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import com.kuvaszuptime.kuvasz.repositories.SettingsRepository
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.validation.Validated
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag

@Controller("$API_V1_PREFIX/settings", produces = [MediaType.APPLICATION_JSON])
@Validated
@Tag(name = "Settings operations")
@SecurityRequirements(
    SecurityRequirement(name = "apiKey"),
    SecurityRequirement(name = "bearerAuth")
)
class SettingsController(private val settingsRepository: SettingsRepository) : SettingsOperations {

    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Successful query",
            content = [Content(schema = Schema(implementation = SettingsDto::class))]
        )
    )
    @ExecuteOn(TaskExecutors.IO)
    override fun getSettings() = settingsRepository.getSettings()
}
