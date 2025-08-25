package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.LegacySettingsDto
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.Operation

@Deprecated("Use SettingsOperationsV2 instead")
interface SettingsOperationsV1 {

    @Operation(summary = "Get the current settings of the application", deprecated = true)
    @Get("/")
    fun getSettings(): LegacySettingsDto
}
