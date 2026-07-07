package com.kuvaszuptime.kuvasz.models.dto.importing

import io.micronaut.core.annotation.Introspected

@Introspected
data class StatusPageImportResultDto(
    val receivedStatusPageCnt: Int,
    val importedStatusPageCnt: Int,
    val deletedStatusPageCount: Int,
)
