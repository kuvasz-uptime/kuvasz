package com.kuvaszuptime.kuvasz.models.dto.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Introspected
data class MaintenanceWindowDetailsDto(
    @param:Schema(description = MaintenanceWindowDocs.ID, required = true)
    val id: Long,
    @param:Schema(description = MaintenanceWindowDocs.NAME, required = true)
    val name: String,
    @param:Schema(description = MaintenanceWindowDocs.DESCRIPTION, required = false)
    val description: String?,
    @param:Schema(description = MaintenanceWindowDocs.ENABLED, required = true)
    val enabled: Boolean,
    @param:Schema(description = MaintenanceWindowDocs.GLOBAL, required = true)
    val global: Boolean,
    @param:Schema(description = MaintenanceWindowDocs.SHOW_ON_STATUS_PAGES, required = true)
    val showOnStatusPages: Boolean,
    @param:Schema(description = MaintenanceWindowDocs.CRON, required = false)
    val cron: String?,
    @param:Schema(description = MaintenanceWindowDocs.START, required = false)
    val start: OffsetDateTime?,
    @param:Schema(description = MaintenanceWindowDocs.DURATION, required = false)
    val duration: String?,
    @param:Schema(description = MaintenanceWindowDocs.MONITORS, required = true)
    val monitors: Set<MonitorID>,
    @param:Schema(description = MaintenanceWindowDocs.INTEGRATIONS, required = true)
    val integrations: Set<IntegrationID>,
    @param:Schema(description = MaintenanceWindowDocs.ACTIVE, required = true)
    val active: Boolean,
    @param:Schema(description = MaintenanceWindowDocs.NEXT_START, required = false)
    val nextStart: OffsetDateTime?,
    @param:Schema(description = MaintenanceWindowDocs.ENDS_AT, required = false)
    val endsAt: OffsetDateTime?,
    @param:Schema(description = MaintenanceWindowDocs.CREATED_AT, required = true)
    val createdAt: OffsetDateTime,
    @param:Schema(description = MaintenanceWindowDocs.UPDATED_AT, required = true)
    val updatedAt: OffsetDateTime,
) {
    companion object {
        fun fromRecord(
            record: MaintenanceWindowRecord,
            active: Boolean,
            nextStart: OffsetDateTime?,
            endsAt: OffsetDateTime?,
        ) = MaintenanceWindowDetailsDto(
            id = record.id,
            name = record.name,
            description = record.description,
            enabled = record.enabled,
            global = record.global,
            showOnStatusPages = record.showOnStatusPages,
            cron = record.cron,
            start = record.start,
            duration = record.duration,
            monitors = record.monitors.toSet(),
            integrations = record.integrations.toSet(),
            active = active,
            nextStart = nextStart,
            endsAt = endsAt,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
    }
}
