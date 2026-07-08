package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.MonitorType
import io.micronaut.core.annotation.Introspected

@Introspected
data class MonitorImportResultDto(
    val dryRun: Boolean,
    val perTypeResults: List<MonitorTypeImportResult> = emptyList(),
)

@Introspected
data class MonitorTypeImportResult(
    val monitorType: MonitorType,
    val receivedCnt: Int,
    val importedCnt: Int,
    val deletedCnt: Int,
)
