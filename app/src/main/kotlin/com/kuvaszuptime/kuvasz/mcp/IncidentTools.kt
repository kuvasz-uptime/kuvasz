package com.kuvaszuptime.kuvasz.mcp

import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentListSchema
import com.kuvaszuptime.kuvasz.mcp.schemas.IncidentSchema
import com.kuvaszuptime.kuvasz.repositories.IncidentRepository
import io.micronaut.mcp.annotations.Tool
import io.micronaut.mcp.annotations.ToolArg
import jakarta.inject.Singleton
import java.time.Duration

private const val INCIDENTS_PERIOD_DAYS = 7L
private val DEFAULT_INCIDENTS_PERIOD = Duration.ofDays(INCIDENTS_PERIOD_DAYS)

@Singleton
class IncidentTools(
    private val incidentRepository: IncidentRepository,
) {

    @Tool(
        name = ToolNames.LIST_INCIDENTS,
        description = "Lists incidents across all monitor types (HTTP, Push, ICMP, SSL). " +
            "Defaults to the last 7 days including resolved incidents. " +
            "Optionally filter by monitor ID.",
        annotations = Tool.ToolAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = false)
    )
    fun listIncidents(
        @ToolArg(description = "Filter by a specific monitor ID. Omit to return incidents for all monitors.")
        monitorId: Long? = null,
        @ToolArg(description = "ISO 8601 duration look-back window, e.g. 'P7D' or 'PT12H'. Defaults to P7D.")
        period: String? = null,
        @ToolArg(description = "Whether to include resolved incidents alongside ongoing ones. Defaults to true.")
        includeResolved: Boolean? = null,
    ): IncidentListSchema =
        IncidentListSchema(
            incidents = incidentRepository.getIncidents(
                monitorId = monitorId,
                period = period.asDuration() ?: DEFAULT_INCIDENTS_PERIOD,
                includeResolved = includeResolved ?: true,
            ).map { IncidentSchema.fromDto(it) }
        )
}
