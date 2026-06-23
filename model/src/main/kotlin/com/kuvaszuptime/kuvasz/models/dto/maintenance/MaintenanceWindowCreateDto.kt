package com.kuvaszuptime.kuvasz.models.dto.maintenance

import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowCreator
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class MaintenanceWindowCreateDto(
    @param:Schema(description = MaintenanceWindowDocs.NAME, required = true)
    override val name: String,
    @param:Schema(description = MaintenanceWindowDocs.DESCRIPTION, required = false)
    override val description: String? = null,
    @param:Schema(
        description = MaintenanceWindowDocs.ENABLED,
        required = false,
        defaultValue = MaintenanceWindowDefaults.ENABLED.toString(),
    )
    override val enabled: Boolean = MaintenanceWindowDefaults.ENABLED,
    @param:Schema(
        description = MaintenanceWindowDocs.GLOBAL,
        required = false,
        defaultValue = MaintenanceWindowDefaults.GLOBAL.toString(),
    )
    override val global: Boolean = MaintenanceWindowDefaults.GLOBAL,
    @param:Schema(
        description = MaintenanceWindowDocs.SHOW_ON_STATUS_PAGES,
        required = false,
        defaultValue = MaintenanceWindowDefaults.SHOW_ON_STATUS_PAGES.toString(),
    )
    override val showOnStatusPages: Boolean = MaintenanceWindowDefaults.SHOW_ON_STATUS_PAGES,
    @param:Schema(description = MaintenanceWindowDocs.CRON, required = false)
    override val cron: String? = null,
    @param:Schema(description = MaintenanceWindowDocs.START, required = false)
    override val start: OffsetDateTime? = null,
    @param:Schema(description = MaintenanceWindowDocs.DURATION, required = false)
    override val duration: String? = null,
    @param:Schema(description = MaintenanceWindowDocs.MONITORS, required = false)
    override val monitors: List<String>? = emptyList(),
    @param:Schema(description = MaintenanceWindowDocs.INTEGRATIONS, required = false)
    override val integrations: List<String>? = emptyList(),
) : MaintenanceWindowCreator
