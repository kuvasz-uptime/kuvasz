package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.LegacySettingsDto
import io.micronaut.http.client.annotation.Client

@Client("/api/v1/settings")
interface SettingsClientV1 : SettingsOperationsV1 {

    override fun getSettings(): LegacySettingsDto
}
