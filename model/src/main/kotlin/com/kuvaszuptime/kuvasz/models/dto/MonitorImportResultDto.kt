package com.kuvaszuptime.kuvasz.models.dto

import io.micronaut.core.annotation.Introspected

@Introspected
data class MonitorImportResultDto(
    val receivedMonitorCnt: Int,
    val importedMonitorCnt: Int,
    val deletedMonitorCount: Int,
)
