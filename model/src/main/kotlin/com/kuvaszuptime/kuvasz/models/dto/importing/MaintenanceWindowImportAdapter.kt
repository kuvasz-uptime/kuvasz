package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.dto.maintenance.MaintenanceWindowExportDto
import com.kuvaszuptime.kuvasz.models.maintenance.MaintenanceWindowCreator
import io.micronaut.core.annotation.Introspected
import jakarta.validation.ValidationException
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

@Introspected
class MaintenanceWindowImportAdapter(dto: MaintenanceWindowExportDto) : MaintenanceWindowCreator {
    override val name: String = dto.name
    override val description: String? = dto.description
    override val enabled: Boolean = dto.enabled
    override val global: Boolean = dto.global
    override val showOnStatusPages: Boolean = dto.showOnStatusPages
    override val cron: String? = dto.cron
    override val duration: String? = dto.duration
    override val monitors: List<String> = dto.monitors.map { it.toString() }
    override val integrations: List<String> = dto.integrations.map { it.toString() }

    override val start: OffsetDateTime? = dto.start?.let { rawStart ->
        try {
            OffsetDateTime.parse(rawStart)
        } catch (e: DateTimeParseException) {
            throw ValidationException("Invalid start date-time for maintenance window '${dto.name}': $rawStart", e)
        }
    }
}
