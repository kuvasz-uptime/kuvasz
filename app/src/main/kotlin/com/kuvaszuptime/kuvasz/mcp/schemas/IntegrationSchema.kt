package com.kuvaszuptime.kuvasz.mcp.schemas

import com.kuvaszuptime.kuvasz.models.handlers.IntegrationConfig
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationEventType
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationType
import com.kuvaszuptime.kuvasz.models.handlers.id
import com.kuvaszuptime.kuvasz.models.handlers.type
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema

@JsonSchema
@Introspected
data class IntegrationListSchema(val integrations: List<IntegrationSchema>)

@Introspected
data class IntegrationSchema(
    val id: String,
    val type: IntegrationType,
    val name: String,
    val enabled: Boolean,
    val global: Boolean,
    val excludedEvents: List<IntegrationEventType>,
) {
    companion object {
        fun fromConfig(config: IntegrationConfig) = IntegrationSchema(
            id = config.id.toString(),
            type = config.type,
            name = config.name,
            enabled = config.enabled,
            global = config.global,
            excludedEvents = config.excludedEvents.orEmpty(),
        )
    }
}
