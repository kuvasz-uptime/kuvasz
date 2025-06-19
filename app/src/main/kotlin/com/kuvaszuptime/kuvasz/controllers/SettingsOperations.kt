package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import io.micronaut.http.annotation.Get
import io.swagger.v3.oas.annotations.Operation

interface SettingsOperations {

    @Operation(summary = "Returns the current settings of the application")
    @Get("/")
    fun getSettings(): SettingsDto
}
