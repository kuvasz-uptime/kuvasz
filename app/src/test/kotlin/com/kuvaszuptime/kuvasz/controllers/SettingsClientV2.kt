package com.kuvaszuptime.kuvasz.controllers

import com.kuvaszuptime.kuvasz.models.dto.SettingsDto
import io.micronaut.http.client.annotation.Client

@Client("/api/v2/settings")
interface SettingsClientV2 : SettingsOperationsV2 {

    override fun getSettings(): SettingsDto
}
