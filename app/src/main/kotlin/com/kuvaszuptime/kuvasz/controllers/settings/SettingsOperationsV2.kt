package com.kuvaszuptime.kuvasz.controllers.settings

import com.kuvaszuptime.kuvasz.models.dto.settings.SettingsDto
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.Operation

interface SettingsOperationsV2 {

    @Operation(summary = "Get the current settings of the application")
    @Get("/")
    fun getSettings(): SettingsDto
}
