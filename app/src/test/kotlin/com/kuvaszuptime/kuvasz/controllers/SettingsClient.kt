package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import io.micronaut.http.client.annotation.Client

@Client("/api/v1/settings")
interface SettingsClient : SettingsOperations {

    override fun getSettings(): SettingsDto
}
