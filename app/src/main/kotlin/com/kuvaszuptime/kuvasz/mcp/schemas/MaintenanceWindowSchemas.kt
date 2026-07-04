package com.kuvaszuptime.kuvasz.mcp.schemas

import com.fasterxml.jackson.annotation.JsonInclude
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowCreateDto
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDefaults
import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowDetailsDto
import com.kuvaszuptime.kuvasz.validation.ValidCron
import com.kuvaszuptime.kuvasz.validation.ValidDuration
import io.micronaut.core.annotation.Introspected
import io.micronaut.jsonschema.JsonSchema
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MaintenanceWindowSummarySchema(
    val id: Long,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val global: Boolean,
    val showOnStatusPages: Boolean,
    val cron: String?,
    val start: OffsetDateTime?,
    val duration: String?,
    val active: Boolean,
    val nextStart: OffsetDateTime?,
    val endsAt: OffsetDateTime?,
) {
    companion object {
        fun fromDto(dto: MaintenanceWindowDetailsDto) = MaintenanceWindowSummarySchema(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            enabled = dto.enabled,
            global = dto.global,
            showOnStatusPages = dto.showOnStatusPages,
            cron = dto.cron,
            start = dto.start,
            duration = dto.duration,
            active = dto.active,
            nextStart = dto.nextStart,
            endsAt = dto.endsAt,
        )
    }
}

@JsonSchema
@Introspected
data class MaintenanceWindowListSchema(
    val maintenanceWindows: List<MaintenanceWindowSummarySchema>,
)

@JsonSchema
@Introspected
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MaintenanceWindowSchema(
    val id: Long,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val global: Boolean,
    val showOnStatusPages: Boolean,
    val cron: String?,
    val start: OffsetDateTime?,
    val duration: String?,
    val monitors: Set<String>,
    val integrations: Set<String>,
    val active: Boolean,
    val nextStart: OffsetDateTime?,
    val endsAt: OffsetDateTime?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromDto(dto: MaintenanceWindowDetailsDto) = MaintenanceWindowSchema(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            enabled = dto.enabled,
            global = dto.global,
            showOnStatusPages = dto.showOnStatusPages,
            cron = dto.cron,
            start = dto.start,
            duration = dto.duration,
            monitors = dto.monitors.map { it.toString() }.toSet(),
            integrations = dto.integrations.map { it.toString() }.toSet(),
            active = dto.active,
            nextStart = dto.nextStart,
            endsAt = dto.endsAt,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
}

@JsonSchema
@Introspected
data class MaintenanceWindowCreatorSchema(
    @get:NotBlank
    val name: String,
    val description: String? = null,
    val enabled: Boolean?,
    val global: Boolean?,
    val showOnStatusPages: Boolean?,
    @get:ValidCron
    val cron: String? = null,
    val start: OffsetDateTime? = null,
    @get:ValidDuration
    val duration: String? = null,
    val monitors: List<String>?,
    val integrations: List<String>?,
) {
    fun toDto() = MaintenanceWindowCreateDto(
        name = name,
        description = description,
        enabled = enabled ?: MaintenanceWindowDefaults.ENABLED,
        global = global ?: MaintenanceWindowDefaults.GLOBAL,
        showOnStatusPages = showOnStatusPages ?: MaintenanceWindowDefaults.SHOW_ON_STATUS_PAGES,
        cron = cron,
        start = start,
        duration = duration,
        monitors = monitors.orEmpty(),
        integrations = integrations.orEmpty(),
    )
}
