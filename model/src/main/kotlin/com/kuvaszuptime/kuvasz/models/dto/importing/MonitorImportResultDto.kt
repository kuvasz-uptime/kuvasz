package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
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
    val imported: List<MonitorID> = emptyList(),
    val deleted: List<MonitorID> = emptyList(),
    val ignoredIntegrations: List<String> = emptyList(),
)
