package com.kuvaszuptime.kuvasz.controllers.settings

import com.kuvaszuptime.kuvasz.models.dto.settings.SettingsDto
import io.micronaut.http.client.annotation.Client

@Client("/api/v2/settings")
interface SettingsClient : SettingsOperations {

    override fun getSettings(): SettingsDto
}
