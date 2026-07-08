package com.kuvaszuptime.kuvasz.models.dto.importing

import com.kuvaszuptime.kuvasz.models.MonitorType
import com.kuvaszuptime.kuvasz.models.monitor.MonitorID
import io.micronaut.core.annotation.Introspected
import io.swagger.v3.oas.annotations.media.Schema

@Introspected
data class MonitorImportResultDto(
    @param:Schema(description = ImportDocs.DRY_RUN, required = true)
    val dryRun: Boolean,
    @param:Schema(description = ImportDocs.PER_TYPE_RESULTS, required = true)
    val perTypeResults: List<MonitorTypeImportResult> = emptyList(),
)

@Introspected
data class MonitorTypeImportResult(
    @param:Schema(description = ImportDocs.MONITOR_TYPE, required = true)
    val monitorType: MonitorType,
    @param:Schema(description = ImportDocs.RECEIVED_CNT, required = true)
    val receivedCnt: Int,
    @param:Schema(description = ImportDocs.IMPORTED_MONITORS, required = true)
    val imported: List<MonitorID> = emptyList(),
    @param:Schema(description = ImportDocs.DELETED_MONITORS, required = true)
    val deleted: List<MonitorID> = emptyList(),
    @param:Schema(description = ImportDocs.IGNORED_INTEGRATIONS, required = true)
    val ignoredIntegrations: List<String> = emptyList(),
)
