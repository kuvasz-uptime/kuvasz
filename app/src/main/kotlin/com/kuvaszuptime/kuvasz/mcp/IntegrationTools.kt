package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.schemas.IntegrationListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IntegrationSchema
import com.kuvaszuptime.kuvasz.services.integrations.IntegrationRepository
import io.micronaut.mcp.annotations.Tool
import jakarta.inject.Singleton

@Singleton
class IntegrationTools(
    private val integrationRepository: IntegrationRepository,
) {

    @Tool(
        name = ToolNames.LIST_INTEGRATIONS,
        description = "Lists all configured integrations (Slack, Discord, Email, PagerDuty, Telegram, Webhook) " +
            "with their type, enabled state, global flag, and excluded event types",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    fun listIntegrations(): IntegrationListSchema =
        IntegrationListSchema(
            integrationRepository.configuredIntegrations.values.map { IntegrationSchema.fromConfig(it) }
        )
}
