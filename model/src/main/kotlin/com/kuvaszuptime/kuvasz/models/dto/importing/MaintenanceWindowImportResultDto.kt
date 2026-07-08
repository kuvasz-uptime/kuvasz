package com.kuvaszuptime.kuvasz.models.dto.importing

import io.micronaut.core.annotation.Introspected

@Introspected
data class MaintenanceWindowImportResultDto(
    val receivedCnt: Int,
    val dryRun: Boolean,
    val imported: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val ignoredMonitors: List<String> = emptyList(),
    val ignoredIntegrations: List<String> = emptyList(),
)
