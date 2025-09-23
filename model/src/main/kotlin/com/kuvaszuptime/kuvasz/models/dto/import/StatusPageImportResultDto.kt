package com.kuvaszuptime.kuvasz.models.dto.import

import io.micronaut.core.annotation.Introspected

@Introspected
data class StatusPageImportResultDto(
    val receivedStatusPageCnt: Int,
    val importedStatusPageCnt: Int,
    val deletedStatusPageCount: Int,
)
