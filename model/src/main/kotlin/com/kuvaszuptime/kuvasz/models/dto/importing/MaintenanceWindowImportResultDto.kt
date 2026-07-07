package com.kuvaszuptime.kuvasz.models.dto.importing

import io.micronaut.core.annotation.Introspected

@Introspected
data class MaintenanceWindowImportResultDto(
    val receivedMaintenanceWindowCnt: Int,
    val importedMaintenanceWindowCnt: Int,
    val deletedMaintenanceWindowCnt: Int,
)
