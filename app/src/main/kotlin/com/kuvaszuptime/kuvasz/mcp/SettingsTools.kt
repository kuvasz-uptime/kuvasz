package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.schemas.AppSettingsSchema
import com.kuvaszuptime.kuvasz.repositories.SettingsRepository
import io.micronaut.mcp.annotations.Tool
import jakarta.inject.Singleton

@Singleton
class SettingsTools(
    private val settingsRepository: SettingsRepository,
) {

    @Tool(
        name = ToolNames.GET_APP_SETTINGS,
        description = "Get the current application settings of this Kuvasz instance, including " +
            "authentication, data retention, language, metrics export, MCP server, and version information",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun getAppSettings(): AppSettingsSchema =
        AppSettingsSchema.fromDto(settingsRepository.getSettings())
}
