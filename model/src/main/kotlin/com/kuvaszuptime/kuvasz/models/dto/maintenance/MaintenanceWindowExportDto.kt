package com.kuvaszuptime.kuvasz.models.dto.maintenance

import com.kuvaszuptime.kuvasz.jooq.tables.records.MaintenanceWindowRecord
import com.kuvaszuptime.kuvasz.models.handlers.IntegrationID
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected

@Introspected
data class MaintenanceWindowExportDto(
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val global: Boolean,
    val showOnStatusPages: Boolean,
    val cron: String?,
    val start: String?,
    val duration: String?,
    val monitors: Set<MonitorID>,
    val integrations: Set<IntegrationID>,
) {
    companion object {
        fun fromRecord(record: MaintenanceWindowRecord) =
            MaintenanceWindowExportDto(
                name = record.name,
                description = record.description,
                enabled = record.enabled,
                global = record.global,
                showOnStatusPages = record.showOnStatusPages,
                cron = record.cron,
                start = record.start?.toString(),
                duration = record.duration,
                monitors = record.monitors.toSet(),
                integrations = record.integrations.toSet(),
            )
    }
}
