package com.kuvaszuptime.kuvasz.models.dto.import

import com.kuvaszuptime.kuvasz.models.MonitorType
import io.micronaut.core.annotation.Introspected

@Introspected
data class MonitorImportResultDto(
    val receivedMonitorCnt: Int,
    val importedMonitorCnt: Int,
    val deletedMonitorCount: Int,
    val dryRun: Boolean = false,
    val perTypeResults: List<MonitorTypeImportResult> = emptyList(),
)

@Introspected
data class MonitorTypeImportResult(
    val monitorType: MonitorType,
    val receivedMonitorCnt: Int,
    val importedMonitorCnt: Int,
    val deletedMonitorCount: Int,
)
