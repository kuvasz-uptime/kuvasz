package com.kuvaszuptime.kuvasz.models.dto.integration

object IntegrationDocs {
    const val ID = "Unique, computed identifier of the integration, e.g. \"email:my-email-notification\""
    const val NAME = "Name of the integration. Must be unique in the context of type."
    const val TYPE = "Type of the integration"
    const val ENABLED = "Whether the integration is enabled"
    const val GLOBAL = "Whether the integration is global (applies to all monitors by default)"
    const val EXCLUDED_EVENTS = "The list of the events that should be excluded from notifications for " +
        "this integration. If empty or null, no event types are excluded."
}
