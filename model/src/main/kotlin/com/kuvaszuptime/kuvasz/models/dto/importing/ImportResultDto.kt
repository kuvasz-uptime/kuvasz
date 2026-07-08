package com.kuvaszuptime.kuvasz.models.dto.importing

import io.micronaut.core.annotation.Introspected

@Introspected
data class ImportResultDto(
    val receivedCnt: Int,
    val importedCnt: Int,
    val deletedCnt: Int,
    val dryRun: Boolean,
)
