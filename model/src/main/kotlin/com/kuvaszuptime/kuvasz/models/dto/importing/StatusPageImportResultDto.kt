package com.kuvaszuptime.kuvasz.models.dto.importing

import io.micronaut.core.annotation.Introspected

@Introspected
data class StatusPageImportResultDto(
    val receivedCnt: Int,
    val dryRun: Boolean,
    val imported: List<String> = emptyList(),
    val deleted: List<String> = emptyList(),
    val ignoredMonitors: List<String> = emptyList(),
)
