package com.kuvaszuptime.kuvasz.models.dto.monitor

import com.kuvaszuptime.kuvasz.models.dto.integration.IntegrationDocs
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.type
import io.swagger.v3.oas.annotations.media.Schema

data class IntegrationDetailsDto(
    @param:Schema(
        description = "Unique identifier for the integration that can be used as a reference on a monitor",
        required = true,
    )
    val id: String,
    @param:Schema(description = "Type of the integration, e.g., EMAIL, WEBHOOK, etc.", required = true)
    val type: IntegrationType,
    @param:Schema(
        description = "Name of the integration, e.g., 'slack-team-devops', 'email-ops-global', etc.",
        required = true,
    )
    val name: String,
    @param:Schema(description = "Whether the integration is enabled or not", required = true)
    val enabled: Boolean,
    @param:Schema(description = "Whether the integration is global or not", required = true)
    val global: Boolean,
    @param:Schema(description = IntegrationDocs.EXCLUDED_EVENTS, required = true)
    val excludedEvents: List<IntegrationEventType>,
) {
    companion object {
        fun fromConfig(config: IntegrationConfig): IntegrationDetailsDto = IntegrationDetailsDto(
            id = config.id.toString(),
            type = config.type,
            name = config.name,
            enabled = config.enabled,
            global = config.global,
            excludedEvents = config.excludedEvents.orEmpty(),
        )
    }
}
