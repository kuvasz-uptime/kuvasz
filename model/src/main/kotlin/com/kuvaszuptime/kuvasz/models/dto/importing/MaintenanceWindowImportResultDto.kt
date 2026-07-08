package com.kuvaszuptime.kuvasz.models.dto.importing

import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class MaintenanceWindowImportResultDto(
    @param:Schema(description = ImportDocs.RECEIVED_CNT, required = true)
    val receivedCnt: Int,
    @param:Schema(description = ImportDocs.DRY_RUN, required = true)
    val dryRun: Boolean,
    @param:Schema(description = ImportDocs.IMPORTED_MAINTENANCE_WINDOWS, required = true)
    val imported: List<String> = emptyList(),
    @param:Schema(description = ImportDocs.DELETED_MAINTENANCE_WINDOWS, required = true)
    val deleted: List<String> = emptyList(),
    @param:Schema(description = ImportDocs.IGNORED_MONITORS, required = true)
    val ignoredMonitors: List<String> = emptyList(),
    @param:Schema(description = ImportDocs.IGNORED_INTEGRATIONS, required = true)
    val ignoredIntegrations: List<String> = emptyList(),
)
