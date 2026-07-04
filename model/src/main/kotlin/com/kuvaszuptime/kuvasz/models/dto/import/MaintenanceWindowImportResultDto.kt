package com.kuvaszuptime.kuvasz.models.dto.import

import io.micronaut.core.annotation.Introspected

@Introspected
data class MaintenanceWindowImportResultDto(
    val receivedMaintenanceWindowCnt: Int,
    val importedMaintenanceWindowCnt: Int,
    val deletedMaintenanceWindowCnt: Int,
)
